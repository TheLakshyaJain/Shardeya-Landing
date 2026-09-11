-- Three broker_partner running totals, each a full recompute (never an
-- increment) -- the same style every other trigger-maintained aggregate in
-- this codebase already uses (plot_sale.total_paid, org_usage,
-- org_metrics), for the same reason: correctness survives any order of
-- operations, concurrent writers, or a row being corrected later, with no
-- drift-prone +1/-1 bookkeeping anywhere.

-- 1. total_commission_earned = SUM(total_commission) across this broker's
-- non-cancelled ledger entries. Fires on both INSERT (a new sale's ledger
-- entry) and the one UPDATE that matters (status flipping to/from
-- CANCELLED, via PlotSaleService.cancel()) -- total_commission itself is
-- a GENERATED column that never changes after insert (base_commission/
-- tier_bonus are snapshotted once, per B-14 §7's own immutability rule),
-- so no other UPDATE case needs to be watched.
CREATE OR REPLACE FUNCTION fn_broker_partner_commission_earned_trigger() RETURNS trigger AS $$
BEGIN
    UPDATE broker_partner
    SET total_commission_earned = (
        SELECT COALESCE(SUM(total_commission), 0)
        FROM commission_ledger_entry
        WHERE broker_partner_id = NEW.broker_partner_id AND status <> 'CANCELLED' AND deleted_at IS NULL
    )
    WHERE id = NEW.broker_partner_id;
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER trg_broker_partner_commission_earned_ins AFTER INSERT ON commission_ledger_entry
    FOR EACH ROW EXECUTE FUNCTION fn_broker_partner_commission_earned_trigger();
CREATE TRIGGER trg_broker_partner_commission_earned_upd AFTER UPDATE OF status ON commission_ledger_entry
    FOR EACH ROW EXECUTE FUNCTION fn_broker_partner_commission_earned_trigger();

-- 2. total_commission_paid = SUM(commission_payment.amount) across every
-- ledger entry belonging to this broker -- a two-hop join (payment ->
-- ledger entry -> broker), fired from the payment side since that's where
-- the actual write happens.
CREATE OR REPLACE FUNCTION fn_broker_partner_commission_paid_trigger() RETURNS trigger AS $$
DECLARE
    v_broker_id UUID;
BEGIN
    SELECT broker_partner_id INTO v_broker_id FROM commission_ledger_entry WHERE id = NEW.commission_ledger_entry_id;

    UPDATE broker_partner
    SET total_commission_paid = (
        SELECT COALESCE(SUM(cp.amount), 0)
        FROM commission_payment cp
        JOIN commission_ledger_entry cle ON cle.id = cp.commission_ledger_entry_id
        WHERE cle.broker_partner_id = v_broker_id
    )
    WHERE id = v_broker_id;
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER trg_broker_partner_commission_paid AFTER INSERT ON commission_payment
    FOR EACH ROW EXECUTE FUNCTION fn_broker_partner_commission_paid_trigger();

-- 3. deals_closed_count = COUNT of this broker's sales currently
-- COMPLETED. Fires whenever a sale's status changes at all (covers both
-- ACTIVE->COMPLETED, the common case, and a COMPLETED sale later being
-- cancelled, B-14 §10's "cancellations reduce the count" edge case) --
-- but only touches broker_partner when the sale actually has one attributed
-- (external-broker/no-broker sales have nothing to update here).
CREATE OR REPLACE FUNCTION fn_broker_partner_deals_closed_trigger() RETURNS trigger AS $$
BEGIN
    IF NEW.broker_partner_id IS NOT NULL AND NEW.status IS DISTINCT FROM OLD.status THEN
        UPDATE broker_partner
        SET deals_closed_count = (
            SELECT COUNT(*) FROM plot_sale
            WHERE broker_partner_id = NEW.broker_partner_id AND status = 'COMPLETED' AND deleted_at IS NULL
        )
        WHERE id = NEW.broker_partner_id;
    END IF;
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER trg_broker_partner_deals_closed AFTER UPDATE OF status ON plot_sale
    FOR EACH ROW EXECUTE FUNCTION fn_broker_partner_deals_closed_trigger();
