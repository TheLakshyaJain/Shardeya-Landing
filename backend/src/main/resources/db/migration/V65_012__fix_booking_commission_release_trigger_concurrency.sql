-- 06-BROKER-NETWORK-ENGINE.md build-order step 11 -- concurrency audit
-- sweep. Real bug found in fn_booking_commission_release_trigger
-- (V65_008), the EXACT same class step 7's own
-- fn_broker_partner_designation_counts_trigger (V65_010) already found and
-- fixed -- but V65_008 predates that discovery and was never revisited.
--
-- THE BUG, confirmed empirically first via a raw two-session psql
-- reproduction (matching step 7's own discipline), not by reasoning alone:
-- the trigger's `v_released` is computed by a plain `SELECT SUM(amount)
-- FROM commission_release ...` BEFORE the `UPDATE booking_commission SET
-- released_amount = v_released ...` that follows. Two concurrent
-- transactions each inserting a commission_release row for the SAME
-- booking_commission_id (a real, reachable shape -- two payments recorded
-- against the same sale at close to the same moment) each compute their
-- own v_released from ONLY their own transaction's not-yet-committed
-- insert (READ COMMITTED never sees another transaction's uncommitted
-- rows), then both attempt the UPDATE; the second one BLOCKS on the
-- first's row lock, and when it unblocks after the first commits, it
-- applies its OWN STALE v_released (a PL/pgSQL variable, fixed before the
-- UPDATE statement ever ran) -- overwriting, not adding to, the first
-- transaction's just-committed value. Reproduced directly: session A
-- inserts a 1000 release, session B (blocked, then unblocked after A
-- commits) inserts a 500 release; the row ends up with released_amount =
-- 500, not the correct 1500 = SUM(commission_release.amount) for that
-- booking. Unlike V65_010's own version of this bug, there is no
-- Hibernate @Version safety net here at all -- this trigger fires as a
-- side effect of a plain commission_release INSERT and issues its own raw
-- UPDATE with no optimistic-lock predicate, so the corruption is
-- completely silent: no exception, no log line, just a wrong
-- released_amount (and therefore a wrong outstandingAmount/status on
-- every dashboard reading it) understating what was actually released.
--
-- THE FIX: identical shape to V65_010's own fix -- acquire the target
-- row's lock as its OWN, separate `PERFORM ... FOR UPDATE` statement
-- BEFORE computing v_released, not implicitly via the later UPDATE's own
-- WHERE clause. Once the PERFORM returns, any other transaction that held
-- this row is guaranteed committed or rolled back, so the SUBSEQUENT
-- `SELECT SUM(amount) ...` -- a genuinely new statement, fresh READ
-- COMMITTED snapshot -- correctly includes everything just committed by
-- whichever transaction this one was blocked behind. Re-verified with the
-- identical two-session reproduction after this fix: the row correctly
-- ends up at released_amount = 1500.
CREATE OR REPLACE FUNCTION fn_booking_commission_release_trigger() RETURNS trigger AS $$
DECLARE
    v_booking_commission_id UUID;
    v_released NUMERIC(19,2);
    v_total NUMERIC(19,2);
    v_current_status booking_commission_status;
BEGIN
    v_booking_commission_id := NEW.booking_commission_id;

    -- Own, separate statement: blocks here (if needed) until any other
    -- transaction holding this row's lock has committed/rolled back. See
    -- this function's own header comment for why the recompute below must
    -- be a genuinely new statement issued AFTER this returns, not folded
    -- into the same statement as this lock acquisition.
    PERFORM 1 FROM booking_commission WHERE id = v_booking_commission_id FOR UPDATE;

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
