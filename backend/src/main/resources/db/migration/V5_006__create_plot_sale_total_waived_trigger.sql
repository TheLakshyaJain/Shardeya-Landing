-- Keeps plot_sale.total_waived (V5_005) in sync with payment_schedule.status
-- flipping into or out of WAIVED. ScheduleService.waive() sets status
-- directly via JPA (not through payment_allocation, unlike amount_allocated/
-- status transitions elsewhere in V3_010), so this fires on UPDATE OF status
-- on payment_schedule itself, not on payment_allocation.
--
-- Recomputes the full SUM rather than incrementing/decrementing -- same
-- "just recompute the aggregate" style as fn_plot_sale_total_paid_trigger,
-- and correctly self-corrects if amount_allocated on an already-WAIVED row
-- changes for any reason (shouldn't happen per V3_010's own "WAIVED is never
-- touched by the allocation trigger" rule, but this stays correct even so).
CREATE OR REPLACE FUNCTION fn_plot_sale_total_waived_trigger() RETURNS trigger AS $$
BEGIN
    UPDATE plot_sale
    SET total_waived = (
        SELECT COALESCE(SUM(expected_amount - amount_allocated), 0)
        FROM payment_schedule
        WHERE plot_sale_id = NEW.plot_sale_id AND status = 'WAIVED' AND deleted_at IS NULL
    )
    WHERE id = NEW.plot_sale_id;
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER trg_plot_sale_total_waived AFTER UPDATE OF status ON payment_schedule
    FOR EACH ROW EXECUTE FUNCTION fn_plot_sale_total_waived_trigger();
