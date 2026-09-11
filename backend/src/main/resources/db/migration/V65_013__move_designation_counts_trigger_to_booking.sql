-- 06-BROKER-NETWORK-ENGINE.md §1/§4/§6/§9, REVISED post-ship behaviour
-- change: counting toward personal/team sales, and promotion evaluation,
-- moves from COMPLETED to BOOKED (i.e. plot_sale creation). This reverses
-- a decision the engine was originally built around (build-order step 7)
-- -- see CLAUDE.md's own "Post-M6.5 Behaviour Change" note for the full
-- before/after and why.
--
-- WHAT CHANGES HERE, PRECISELY:
--   1. The trigger now fires on INSERT too (a brand-new booking), not
--      only on UPDATE OF status. It still fires on UPDATE OF status,
--      because that's how a CANCELLATION reaches this table -- and under
--      the new rule, cancellation is the ONLY thing that ever reverses a
--      count (§9), so that half of the trigger's job is unchanged in
--      spirit, just now load-bearing for every booking, not only ones
--      that had reached COMPLETED.
--   2. The recompute predicate changes from `status = 'COMPLETED' AND
--      total_waived = 0` to simply `status <> 'CANCELLED'`. A booking now
--      counts the instant it exists (status is always ACTIVE at
--      creation, never inserted pre-cancelled), keeps counting through
--      COMPLETED (no functional change to the count on that transition,
--      though the trigger still harmlessly fires and re-locks/recomputes
--      -- see below for why that's deliberately left as-is), and stops
--      counting the moment it's CANCELLED. total_waived is no longer read
--      by this function at all -- waiver is moot for counting once
--      counting happens before any payment could even exist (§4).
--
-- WHY THE TRIGGER STILL FIRES (HARMLESSLY) ON A COMPLETED TRANSITION:
-- deliberately NOT narrowing the UPDATE OF status condition to "only
-- transitions into/out of CANCELLED" -- the recompute is a pure,
-- idempotent function of current DB state (this codebase's own
-- established "always full recompute, never increment" philosophy), so
-- firing it on a transition that happens not to change the answer is
-- wasteful by a few extra row-locks, never incorrect. Narrowing the
-- condition would be a correctness-neutral micro-optimisation not worth
-- the extra fragility of a more complex trigger-firing predicate.
--
-- CONCURRENCY: the exact same PERFORM ... FOR UPDATE-before-recompute
-- protection from step 7 (and the trigger-vs-Hibernate lock-ordering fix
-- from step 11) is preserved verbatim -- only the firing condition and
-- the recompute predicate changed; the locking discipline inside the loop
-- is byte-for-byte identical.
CREATE OR REPLACE FUNCTION fn_broker_partner_designation_counts_trigger() RETURNS trigger AS $$
DECLARE
    v_commission_type broker_commission_type;
    rec RECORD;
BEGIN
    IF TG_OP = 'UPDATE' AND NEW.status IS NOT DISTINCT FROM OLD.status THEN
        RETURN NEW;
    END IF;
    IF NEW.broker_partner_id IS NULL THEN
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
        -- See V65_010's own header comment for the full reasoning.
        PERFORM 1 FROM broker_partner WHERE id = rec.broker_id FOR UPDATE;

        IF rec.depth = 0 THEN
            UPDATE broker_partner
            SET personal_successful_bookings = (
                    SELECT COUNT(*) FROM plot_sale
                    WHERE broker_partner_id = rec.broker_id AND status <> 'CANCELLED'
                      AND deleted_at IS NULL
                ),
                team_successful_bookings = (
                    SELECT COUNT(*) FROM plot_sale ps
                    JOIN broker_network bn2 ON bn2.descendant_broker_id = ps.broker_partner_id
                    WHERE bn2.ancestor_broker_id = rec.broker_id AND ps.status <> 'CANCELLED'
                      AND ps.deleted_at IS NULL
                )
            WHERE id = rec.broker_id;
        ELSE
            UPDATE broker_partner
            SET team_successful_bookings = (
                    SELECT COUNT(*) FROM plot_sale ps
                    JOIN broker_network bn2 ON bn2.descendant_broker_id = ps.broker_partner_id
                    WHERE bn2.ancestor_broker_id = rec.broker_id AND ps.status <> 'CANCELLED'
                      AND ps.deleted_at IS NULL
                )
            WHERE id = rec.broker_id;
        END IF;
    END LOOP;

    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

DROP TRIGGER trg_broker_partner_designation_counts ON plot_sale;
CREATE TRIGGER trg_broker_partner_designation_counts AFTER INSERT OR UPDATE OF status ON plot_sale
    FOR EACH ROW EXECUTE FUNCTION fn_broker_partner_designation_counts_trigger();
