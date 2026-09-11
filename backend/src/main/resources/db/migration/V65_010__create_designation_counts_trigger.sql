-- 06-BROKER-NETWORK-ENGINE.md §4/§14(§43), build-order step 7 --
-- personal_successful_bookings/team_successful_bookings (columns since
-- V65_004, always insertable=false/updatable=false on the JPA side,
-- genuinely wired up for the first time here) are trigger-maintained, full
-- recompute, same "never increment, always full COUNT(*)" philosophy as
-- every other aggregate in this codebase (org_usage, deals_closed_count,
-- booking_commission.released_amount, ...).
--
-- CONCURRENCY (§43, this step's own explicit hard requirement) -- and a
-- REAL bug this migration's own first draft had, caught only by an
-- empirical two-session psql reproduction, not by reasoning about Postgres
-- from memory: a naive "just UPDATE the row, Postgres row-locking will
-- serialize concurrent writers for free" design is WRONG here. Postgres's
-- READ COMMITTED isolation gives each *statement* a fresh snapshot at the
-- moment *that statement begins* -- but when an UPDATE has to BLOCK
-- waiting for another transaction's lock on its target row, the snapshot
-- for that ENTIRE statement (including any subquery inside its SET
-- clause) was already fixed *before* the block, and is NOT refreshed once
-- the lock is released and the statement proceeds. Only the target ROW
-- ITSELF is re-fetched (Postgres's "EvalPlanQual" re-check, which exists
-- specifically to avoid clobbering a concurrent change to that one row) --
-- an aggregate subquery over an unrelated table (here, `plot_sale`) keeps
-- reading the *pre-block* snapshot, silently missing whatever the other
-- transaction committed while this one was waiting. Confirmed directly: a
-- two-session reproduction with `UPDATE t SET val = (SELECT COUNT(*) FROM
-- source) WHERE id = 1` -- session B blocked on session A's lock, then
-- unblocked after A inserted a row and committed -- computed val = 1, not
-- 2, i.e. it never saw A's insert despite executing strictly after A's
-- commit. The original design's own concurrent test caught this
-- immediately ("expected: 2 but was: 1" on the shared upline's team
-- count) -- exactly the kind of bug §43 warned this area would have if
-- concurrency weren't taken seriously from the start.
--
-- THE FIX: acquire the row lock as its OWN, separate statement (`PERFORM
-- 1 ... FOR UPDATE`) *before* the recompute UPDATE, not as a side effect
-- of the UPDATE's own WHERE clause. Each statement in a PL/pgSQL function
-- body gets its own fresh READ COMMITTED snapshot when *that statement*
-- starts (confirmed by the same empirical reproduction, this time
-- splitting the blocking SELECT ... FOR UPDATE from the subsequent
-- recompute UPDATE into two statements -- val correctly became 2). By the
-- time the PERFORM's FOR UPDATE returns, any transaction that held this
-- row is guaranteed committed (or rolled back); the UPDATE that follows,
-- as a genuinely new statement, takes a fresh snapshot that correctly
-- includes everything the other transaction committed.
--
-- Self (depth 0) then ancestors nearest-first (ORDER BY depth ASC) is
-- what makes the lock ACQUISITION order deadlock-safe. broker_network is
-- a strict tree (§10: one direct upline only, no cycles), so for any two
-- brokers X and Y where X is an ancestor of Y, X is an ancestor of Y in
-- EVERY descendant's closure that includes both -- their relative order
-- is fixed regardless of which seller's completion you're looking at.
-- Processing "nearest first, ascending depth" for every trigger firing
-- means any two transactions that share common ancestors always attempt
-- to lock those shared rows in the same relative order (nearer before
-- farther), never crossed -- the standard "always acquire locks in a
-- consistent global order" deadlock-avoidance rule, satisfied for free by
-- this tree's own structure.
--
-- "A waived remainder does NOT count as complete" (§1's own COMPLETED
-- definition, this engine's own narrower meaning of the word -- NOT a
-- redefinition of plot_sale.status itself, which still correctly reaches
-- COMPLETED via a waived balance per the existing, unrelated M5 fix; see
-- CLAUDE.md's "Balance Remaining" section) is enforced by filtering the
-- recompute's own COUNT(*) on total_waived = 0, not by skipping the
-- trigger for a waived sale -- the trigger still fires and still
-- correctly recomputes every OTHER qualifying sale's contribution; this
-- one sale's own row simply never satisfies the filter, so it's silently,
-- correctly excluded from every affected broker's count.
CREATE OR REPLACE FUNCTION fn_broker_partner_designation_counts_trigger() RETURNS trigger AS $$
DECLARE
    v_commission_type broker_commission_type;
    rec RECORD;
BEGIN
    IF NEW.broker_partner_id IS NULL OR NEW.status IS NOT DISTINCT FROM OLD.status THEN
        RETURN NEW;
    END IF;

    SELECT commission_type INTO v_commission_type FROM broker_partner WHERE id = NEW.broker_partner_id;
    IF v_commission_type IS DISTINCT FROM 'DESIGNATION' THEN
        RETURN NEW;
    END IF;

    FOR rec IN
        SELECT bn.ancestor_broker_id AS broker_id, bn.depth
        FROM broker_network bn
        WHERE bn.descendant_broker_id = NEW.broker_partner_id
        ORDER BY bn.depth ASC
    LOOP
        -- Own, separate statement: blocks here (if needed) until any other
        -- transaction holding this row's lock has committed/rolled back.
        -- See this function's own header comment for why this must be a
        -- distinct statement from the recompute UPDATE below, not folded
        -- into its WHERE clause.
        PERFORM 1 FROM broker_partner WHERE id = rec.broker_id FOR UPDATE;

        IF rec.depth = 0 THEN
            UPDATE broker_partner
            SET personal_successful_bookings = (
                    SELECT COUNT(*) FROM plot_sale
                    WHERE broker_partner_id = rec.broker_id AND status = 'COMPLETED'
                      AND total_waived = 0 AND deleted_at IS NULL
                ),
                team_successful_bookings = (
                    SELECT COUNT(*) FROM plot_sale ps
                    JOIN broker_network bn2 ON bn2.descendant_broker_id = ps.broker_partner_id
                    WHERE bn2.ancestor_broker_id = rec.broker_id AND ps.status = 'COMPLETED'
                      AND ps.total_waived = 0 AND ps.deleted_at IS NULL
                )
            WHERE id = rec.broker_id;
        ELSE
            UPDATE broker_partner
            SET team_successful_bookings = (
                    SELECT COUNT(*) FROM plot_sale ps
                    JOIN broker_network bn2 ON bn2.descendant_broker_id = ps.broker_partner_id
                    WHERE bn2.ancestor_broker_id = rec.broker_id AND ps.status = 'COMPLETED'
                      AND ps.total_waived = 0 AND ps.deleted_at IS NULL
                )
            WHERE id = rec.broker_id;
        END IF;
    END LOOP;

    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER trg_broker_partner_designation_counts AFTER UPDATE OF status ON plot_sale
    FOR EACH ROW EXECUTE FUNCTION fn_broker_partner_designation_counts_trigger();
