-- 06-BROKER-NETWORK-ENGINE.md §8a -- maintains booking_commission.paid_amount
-- (already a real column since V65_006, but never written to before this
-- round -- see that table's own class javadoc) as a full recompute off
-- broker_commission_payment_allocation, the exact "always a full SUM,
-- never an incrementally-updated running counter" philosophy every other
-- trigger-maintained aggregate in this codebase uses (plot_sale.total_paid,
-- booking_commission.released_amount itself, org_usage, ...). Once this
-- writes a real value, booking_commission.pending_amount -- already a
-- GENERATED column, released_amount - paid_amount -- automatically and
-- correctly becomes "Commission Due" with zero further schema changes;
-- that column has been sitting there unused since step 5/6 anticipating
-- exactly this.
--
-- Written with the lock-then-recompute discipline from day one, not
-- discovered the hard way a second time: V65_008 (the release trigger)
-- and V65_010 (the designation-counts trigger) both originally computed
-- their aggregate via a plain SELECT-then-UPDATE and were later found, via
-- real two-session psql reproductions, to silently lose updates under
-- concurrent writers (a blocked-then-unblocked UPDATE's own subquery
-- keeps its pre-block READ COMMITTED snapshot). Both were fixed the same
-- way (V65_012/the step-7 fix): acquire the target row's lock as its own,
-- separate PERFORM ... FOR UPDATE statement BEFORE computing the
-- aggregate, so the recompute is a genuinely new statement issued only
-- after any concurrent writer holding this row is guaranteed committed or
-- rolled back. This trigger applies that same lesson immediately, rather
-- than waiting for a future round to rediscover the identical bug a third
-- time -- see BrokerCommissionPaymentService's own javadoc for how the
-- Java-layer PESSIMISTIC_WRITE lock it separately acquires before
-- allocating reinforces this at the transaction level too (defense in
-- depth, not redundant machinery: the service-layer lock is what makes
-- the hard payout cap itself concurrency-safe; this trigger-layer lock is
-- what makes the aggregate column itself concurrency-safe regardless of
-- which code path ever inserts an allocation row).
CREATE OR REPLACE FUNCTION fn_booking_commission_paid_amount_trigger() RETURNS trigger AS $$
DECLARE
    v_booking_commission_id UUID;
    v_paid NUMERIC(19,2);
BEGIN
    v_booking_commission_id := NEW.booking_commission_id;

    PERFORM 1 FROM booking_commission WHERE id = v_booking_commission_id FOR UPDATE;

    SELECT COALESCE(SUM(amount), 0) INTO v_paid
    FROM broker_commission_payment_allocation WHERE booking_commission_id = v_booking_commission_id;

    UPDATE booking_commission
    SET paid_amount = v_paid
    WHERE id = v_booking_commission_id;

    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER trg_booking_commission_paid_amount AFTER INSERT ON broker_commission_payment_allocation
    FOR EACH ROW EXECUTE FUNCTION fn_booking_commission_paid_amount_trigger();
