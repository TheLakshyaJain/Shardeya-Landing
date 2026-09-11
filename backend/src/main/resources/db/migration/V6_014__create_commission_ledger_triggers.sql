-- Mirrors payment_schedule's own amount_allocated/status trigger shape
-- (V3_010) exactly: amount_paid is a full recompute (SUM), never an
-- increment, and CANCELLED is the one status this trigger never
-- overwrites -- it's a deliberate, reason-required manual action
-- (PlotSaleService.cancel(), Java-side) the same way WAIVED is for
-- payment_schedule, not something a later payment should be able to
-- silently undo by recomputing the derived status.
CREATE OR REPLACE FUNCTION fn_commission_ledger_payment_trigger() RETURNS trigger AS $$
DECLARE
    v_ledger_id UUID;
    v_paid NUMERIC(19,2);
    v_total NUMERIC(19,2);
    v_current_status commission_ledger_status;
BEGIN
    v_ledger_id := NEW.commission_ledger_entry_id;

    SELECT COALESCE(SUM(amount), 0) INTO v_paid
    FROM commission_payment WHERE commission_ledger_entry_id = v_ledger_id;

    SELECT total_commission, status INTO v_total, v_current_status
    FROM commission_ledger_entry WHERE id = v_ledger_id;

    UPDATE commission_ledger_entry
    SET amount_paid = v_paid,
        status = CASE
            WHEN v_current_status = 'CANCELLED' THEN 'CANCELLED'
            WHEN v_paid >= v_total THEN 'PAID'
            WHEN v_paid > 0 THEN 'PARTIALLY_PAID'
            ELSE 'PENDING'
        END::commission_ledger_status
    WHERE id = v_ledger_id;

    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

-- commission_payment has no soft-delete (V6_008, RULE-blocked DELETE
-- instead) so this only ever needs to fire on INSERT -- a reversal is
-- itself a new (negative-amount) INSERT, same as payment_record's own
-- total_paid trigger.
CREATE TRIGGER trg_commission_ledger_payment AFTER INSERT ON commission_payment
    FOR EACH ROW EXECUTE FUNCTION fn_commission_ledger_payment_trigger();
