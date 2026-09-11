-- Mirrors fn_commission_ledger_payment_trigger (V6_014) exactly:
-- released_amount is a full recompute (SUM), never an increment, and
-- CANCELLED is the one status this trigger never overwrites -- reserved
-- for build-order step 9's future cancellation/recovery logic, the same
-- deliberate carve-out V6_014 already uses for commission_ledger_entry.
CREATE OR REPLACE FUNCTION fn_booking_commission_release_trigger() RETURNS trigger AS $$
DECLARE
    v_booking_commission_id UUID;
    v_released NUMERIC(19,2);
    v_total NUMERIC(19,2);
    v_current_status booking_commission_status;
BEGIN
    v_booking_commission_id := NEW.booking_commission_id;

    SELECT COALESCE(SUM(amount), 0) INTO v_released
    FROM commission_release WHERE booking_commission_id = v_booking_commission_id;

    SELECT total_amount, status INTO v_total, v_current_status
    FROM booking_commission WHERE id = v_booking_commission_id;

    UPDATE booking_commission
    SET released_amount = v_released,
        status = CASE
            WHEN v_current_status = 'CANCELLED' THEN 'CANCELLED'
            WHEN v_released >= v_total AND v_total > 0 THEN 'FULLY_RELEASED'
            WHEN v_released > 0 THEN 'PARTIALLY_RELEASED'
            ELSE 'PENDING'
        END::booking_commission_status
    WHERE id = v_booking_commission_id;

    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

-- commission_release has no soft-delete (RULE-blocked DELETE instead, same
-- as commission_payment) so this only ever needs to fire on INSERT -- a
-- correction is itself a new (possibly negative-amount) INSERT.
CREATE TRIGGER trg_booking_commission_release AFTER INSERT ON commission_release
    FOR EACH ROW EXECUTE FUNCTION fn_booking_commission_release_trigger();
