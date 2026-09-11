-- B-08 §3 rollup for fast summary cards, refreshed every 15 min by
-- OrgMetricsReconciliationJob; the current month is also always computed
-- live (see FinancialService) so a payment just recorded is immediately
-- reflected -- the view is only ever a fast path for prior months.
--
-- Deviation from 03-BUILDER-MODULES.md's literal example: its
-- `WHERE deleted_at IS NULL` clause doesn't apply here -- payment_record has
-- no deleted_at column at all (it's append-only per M3's own documented
-- immutability contract; a DB RULE blocks DELETE outright, and reversals are
-- modeled as a second negative-amount row, not a soft-delete). Omitted
-- rather than copied verbatim, since the column doesn't exist to filter on.
CREATE MATERIALIZED VIEW mv_org_revenue_monthly AS
SELECT org_id, project_id, date_trunc('month', paid_on) AS month,
       SUM(amount) AS collected, COUNT(*) AS payment_count
FROM payment_record
GROUP BY 1, 2, 3;

CREATE UNIQUE INDEX ux_mv_org_revenue_monthly ON mv_org_revenue_monthly(org_id, project_id, month);
