-- Real bug found by a user: waiving an instalment (ScheduleService.waive())
-- marks that payment_schedule row WAIVED but plot_sale.balance_due was
-- GENERATED ALWAYS AS (deal_value - total_paid) -- a formula with no term
-- for waived-but-never-collected amounts at all. So forgiving, say, a
-- Rs 10,000 shortfall on a partially-paid instalment correctly stopped it
-- being chased on the calendar/Tracker, but the sale's own "Balance
-- Remaining" figure kept counting that same Rs 10,000 as still owed --
-- exactly contradicting what "waive" is supposed to mean.
--
-- total_waived mirrors total_paid's own shape (trigger-maintained from a
-- SUM query, never written by application code). It sums
-- (expected_amount - amount_allocated) for every WAIVED schedule row on the
-- sale -- the never-collected portion, which is the only part that needs
-- forgiving (anything already allocated is real money already received,
-- unaffected by a later waive).
ALTER TABLE plot_sale ADD COLUMN total_waived NUMERIC(19,2) NOT NULL DEFAULT 0;

-- Backfill for every sale that already has a waived schedule row from
-- before this column existed.
UPDATE plot_sale ps
SET total_waived = COALESCE((
    SELECT SUM(sch.expected_amount - sch.amount_allocated)
    FROM payment_schedule sch
    WHERE sch.plot_sale_id = ps.id AND sch.status = 'WAIVED' AND sch.deleted_at IS NULL
), 0);

-- A GENERATED column's expression can't be altered in place -- drop and
-- recreate it (safe: balance_due is purely derived, never independently
-- entered data, and total_waived above is already backfilled before this
-- recomputes every row).
ALTER TABLE plot_sale DROP COLUMN balance_due;
ALTER TABLE plot_sale ADD COLUMN balance_due NUMERIC(19,2)
    GENERATED ALWAYS AS (deal_value - total_paid - total_waived) STORED;
