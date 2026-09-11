-- B-04/B-05: total_paid and schedule status/amount_allocated are
-- trigger-maintained, never written directly by application code (same
-- "derived figures... never computed ad hoc" rule as B-05 §7 states for the
-- UI layer, applied at the DB layer too since two ledgers -- payment_record
-- and payment_allocation -- both feed these numbers and must never drift
-- out of sync with each other).

-- plot_sale.total_paid = SUM(payment_record.amount) for that sale. Fires on
-- INSERT only -- payment_record is insert-only (V3_003's own no-delete RULE
-- and CLAUDE.md rule #7: corrections are reversals, i.e. new negative rows,
-- never updates).
CREATE OR REPLACE FUNCTION fn_plot_sale_total_paid_trigger() RETURNS trigger AS $$
BEGIN
    UPDATE plot_sale
    SET total_paid = (SELECT COALESCE(SUM(amount), 0) FROM payment_record WHERE plot_sale_id = NEW.plot_sale_id)
    WHERE id = NEW.plot_sale_id;
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER trg_plot_sale_total_paid AFTER INSERT ON payment_record
    FOR EACH ROW EXECUTE FUNCTION fn_plot_sale_total_paid_trigger();

-- payment_schedule.amount_allocated + status from SUM(payment_allocation.amount)
-- for that schedule. Fires on allocation insert AND soft-delete (a reversal's
-- own allocations are inserted separately, but an allocation can also be
-- soft-deleted directly if a payment is corrected before any reversal
-- mechanism consumes it -- covering both keeps amount_allocated from ever
-- silently drifting).
--
-- WAIVED is the one status this trigger never touches -- it's a deliberate,
-- reason-required manual action (B-05 §7: "never silently reduces the
-- expected total"), not something a later payment should be able to undo by
-- recomputing amounts. OVERDUE, by contrast, CAN be cleared here: if a
-- schedule was flagged OVERDUE (by the scheduled job, not this trigger) and
-- a payment later fully covers it, it should become PAID immediately rather
-- than staying OVERDUE until the next scheduled sweep.
CREATE OR REPLACE FUNCTION fn_payment_schedule_allocation_trigger() RETURNS trigger AS $$
DECLARE
    v_schedule_id UUID;
    v_allocated NUMERIC(19,2);
    v_expected NUMERIC(19,2);
    v_due_date DATE;
    v_current_status payment_schedule_status;
BEGIN
    v_schedule_id := COALESCE(NEW.payment_schedule_id, OLD.payment_schedule_id);

    SELECT expected_amount, due_date, status INTO v_expected, v_due_date, v_current_status
    FROM payment_schedule WHERE id = v_schedule_id;

    SELECT COALESCE(SUM(amount), 0) INTO v_allocated
    FROM payment_allocation WHERE payment_schedule_id = v_schedule_id AND deleted_at IS NULL;

    UPDATE payment_schedule
    SET amount_allocated = v_allocated,
        status = CASE
            WHEN v_current_status = 'WAIVED' THEN 'WAIVED'
            WHEN v_allocated >= v_expected THEN 'PAID'
            WHEN v_allocated > 0 THEN 'PARTIALLY_PAID'
            WHEN v_due_date < CURRENT_DATE THEN 'OVERDUE'
            ELSE 'PENDING'
        END::payment_schedule_status
    WHERE id = v_schedule_id;

    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER trg_payment_schedule_alloc_ins AFTER INSERT ON payment_allocation
    FOR EACH ROW EXECUTE FUNCTION fn_payment_schedule_allocation_trigger();
CREATE TRIGGER trg_payment_schedule_alloc_upd AFTER UPDATE OF deleted_at ON payment_allocation
    FOR EACH ROW EXECUTE FUNCTION fn_payment_schedule_allocation_trigger();
