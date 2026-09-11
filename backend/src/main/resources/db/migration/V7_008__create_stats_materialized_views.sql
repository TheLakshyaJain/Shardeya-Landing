-- B-15 §3, the four named materialised views verbatim. Refreshed every 15
-- minutes by StatsRefreshJob (CONCURRENTLY, hence the unique indexes below
-- on every one). NOT every §21.1 chart reads from a matview, deliberately:
-- Plot Status Breakdown (plot.status is already trigger/service-maintained,
-- cheap to read live), Conversion Funnel (B-15 §7 explicitly warns
-- current-status counting -- exactly what a status-snapshot view like
-- mv_lead_funnel gives -- "understates conversion badly"; the funnel needs
-- real cohort tracing over customer+interaction, live), Collection vs
-- Target, and Overdue Trend are all computed live in StatsService instead,
-- the same "correctness over a stale snapshot" call M5's FinancialService
-- overdue-figures fix already established for this codebase (see CLAUDE.md).
CREATE MATERIALIZED VIEW mv_monthly_sales AS
SELECT org_id, project_id, date_trunc('month', purchase_date) AS month,
       COUNT(*) AS plots_sold, SUM(deal_value) AS sale_value
FROM plot_sale WHERE status <> 'CANCELLED' AND deleted_at IS NULL
GROUP BY 1, 2, 3;

CREATE UNIQUE INDEX ux_mv_monthly_sales ON mv_monthly_sales(org_id, project_id, month);

CREATE MATERIALIZED VIEW mv_lead_funnel AS
SELECT org_id, interested_project_id AS project_id, source, status, assigned_to,
       COUNT(*) AS lead_count
FROM customer WHERE deleted_at IS NULL
GROUP BY 1, 2, 3, 4, 5;

-- assigned_to and project_id/status can each be NULL (unassigned lead / no
-- interested project / every lead has a status though it's NOT NULL) --
-- NULLS NOT DISTINCT so two genuinely-unassigned-lead groups collapse into
-- one row instead of the index rejecting the second as a false "duplicate"
-- vs the first NULL, or (with plain UNIQUE) never colliding at all and
-- silently allowing duplicate rows through REFRESH.
CREATE UNIQUE INDEX ux_mv_lead_funnel ON mv_lead_funnel(org_id, project_id, source, status, assigned_to) NULLS NOT DISTINCT;

CREATE MATERIALIZED VIEW mv_broker_performance AS
SELECT org_id, broker_partner_id, project_id,
       COUNT(*) FILTER (WHERE status = 'COMPLETED') AS deals_closed,
       COALESCE(SUM(deal_value) FILTER (WHERE status <> 'CANCELLED'), 0) AS revenue_generated
FROM plot_sale WHERE deleted_at IS NULL AND broker_partner_id IS NOT NULL
GROUP BY 1, 2, 3;

CREATE UNIQUE INDEX ux_mv_broker_performance ON mv_broker_performance(org_id, broker_partner_id, project_id);

-- Per-user monthly rollup across four independent source tables/columns
-- (leads assigned, sales handled, interactions logged, payments received) --
-- a FULL JOIN across four keyed subqueries rather than one denormalised
-- source table, since none of the four events share a single owning table.
CREATE MATERIALIZED VIEW mv_staff_activity AS
WITH leads AS (
    SELECT org_id, assigned_to AS user_id, date_trunc('month', created_at) AS month, COUNT(*) AS leads_handled
    FROM customer WHERE deleted_at IS NULL AND assigned_to IS NOT NULL
    GROUP BY 1, 2, 3
), deals AS (
    SELECT org_id, handled_by AS user_id, date_trunc('month', purchase_date) AS month, COUNT(*) AS deals_closed
    FROM plot_sale WHERE deleted_at IS NULL AND handled_by IS NOT NULL AND status = 'COMPLETED'
    GROUP BY 1, 2, 3
), followups AS (
    SELECT org_id, conducted_by AS user_id, date_trunc('month', occurred_on) AS month, COUNT(*) AS follow_ups_logged
    FROM interaction WHERE conducted_by IS NOT NULL
    GROUP BY 1, 2, 3
), payments AS (
    SELECT org_id, received_by AS user_id, date_trunc('month', paid_on) AS month, COUNT(*) AS payments_recorded
    FROM payment_record WHERE received_by IS NOT NULL AND amount > 0
    GROUP BY 1, 2, 3
), keys AS (
    SELECT org_id, user_id, month FROM leads
    UNION SELECT org_id, user_id, month FROM deals
    UNION SELECT org_id, user_id, month FROM followups
    UNION SELECT org_id, user_id, month FROM payments
)
SELECT k.org_id, k.user_id, k.month,
       COALESCE(l.leads_handled, 0) AS leads_handled,
       COALESCE(d.deals_closed, 0) AS deals_closed,
       COALESCE(f.follow_ups_logged, 0) AS follow_ups_logged,
       COALESCE(p.payments_recorded, 0) AS payments_recorded
FROM keys k
LEFT JOIN leads l ON l.org_id = k.org_id AND l.user_id = k.user_id AND l.month = k.month
LEFT JOIN deals d ON d.org_id = k.org_id AND d.user_id = k.user_id AND d.month = k.month
LEFT JOIN followups f ON f.org_id = k.org_id AND f.user_id = k.user_id AND f.month = k.month
LEFT JOIN payments p ON p.org_id = k.org_id AND p.user_id = k.user_id AND p.month = k.month;

CREATE UNIQUE INDEX ux_mv_staff_activity ON mv_staff_activity(org_id, user_id, month);

-- Postgres has no RLS on materialized views at all (ALTER MATERIALIZED VIEW
-- ... ENABLE ROW LEVEL SECURITY is not valid syntax) -- unlike every other
-- tenant table in this codebase, RLS is NOT available even as a backstop
-- here. StatsService's own explicit `WHERE org_id = :orgId` on every query
-- against these four views is therefore the ONLY isolation mechanism, not
-- the usual "backstop behind RLS" -- CLAUDE.md rule #1 still applies, there
-- is just one fewer layer under it for this one case. Explicit GRANTs below
-- are redundant with V0_005's ALTER DEFAULT PRIVILEGES (which does cover
-- matviews), kept anyway given this codebase's repeat history of
-- GRANT-shaped "permission denied" bugs (see CLAUDE.md M1/M5 notes) --
-- cheap to be explicit, expensive to silently rely on a default working.
GRANT SELECT ON mv_monthly_sales, mv_lead_funnel, mv_broker_performance, mv_staff_activity TO shardeya_app;
