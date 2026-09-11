-- Real bug found on a live account: every stats chart backed by a
-- materialised view (Monthly Sales, Revenue by Project, the fast-path
-- Top Brokers/Leads by Source, Staff Performance) was silently empty for
-- EVERY org, always -- not stale, genuinely empty, confirmed by running
-- REFRESH manually and inspecting the resulting row count directly.
--
-- Root cause: V7_012's own comment reasoned "isolation is entirely the
-- explicit WHERE org_id=... predicate... unaffected by who owns the
-- underlying view" -- true for READS of the matview itself (Postgres has
-- no RLS on materialized views at all), but it missed that REFRESH
-- MATERIALIZED VIEW re-executes the view's *defining* query against its
-- SOURCE tables (plot_sale, customer, interaction, payment_record,
-- broker_partner), and those source tables DO have FORCE ROW LEVEL
-- SECURITY. StatsRefreshJob runs as shardeya_app (RLS-enforced, not
-- BYPASSRLS) with no tenant context bound at all -- it's a cross-org
-- scheduled job, no single org's context makes sense to bind for it.
-- Confirmed empirically: SET ROLE shardeya_app; REFRESH MATERIALIZED VIEW
-- mv_monthly_sales; leaves the view at 0 rows even with real sales in the
-- table, while the identical query run as the owning superuser (RLS
-- bypassed by ownership) returns every org's rows correctly.
--
-- Same fix shape as V1_009's shardeya_authlookup: a role scoped to
-- exactly one code path (StatsRefreshJob, via its own dedicated
-- datasource -- never the general JPA datasource), BYPASSRLS because a
-- legitimate cross-tenant maintenance operation like this is exactly what
-- RLS-as-a-backstop should never accidentally swallow.
DO $$
BEGIN
    IF NOT EXISTS (SELECT FROM pg_roles WHERE rolname = 'shardeya_statsrefresh') THEN
        CREATE ROLE shardeya_statsrefresh LOGIN PASSWORD 'shardeya_statsrefresh' BYPASSRLS;
    END IF;
END
$$;

GRANT USAGE ON SCHEMA public TO shardeya_statsrefresh;
-- Exactly the tables the four matview definitions read from (V7_008) --
-- app_user is joined separately, inside StatsService's own live queries
-- against these matviews, which already run with a real tenant context
-- bound, so it doesn't need a grant here.
GRANT SELECT ON plot_sale, customer, interaction, payment_record, broker_partner TO shardeya_statsrefresh;

-- REFRESH MATERIALIZED VIEW has no GRANT-based escape hatch (V7_012's own
-- lesson) -- it requires actual ownership. shardeya_app can't both own
-- these (to satisfy that) and be RLS-enforced against their own source
-- tables (to satisfy tenant isolation everywhere else) at the same time,
-- so ownership moves to the new BYPASSRLS role instead.
ALTER MATERIALIZED VIEW mv_monthly_sales OWNER TO shardeya_statsrefresh;
ALTER MATERIALIZED VIEW mv_lead_funnel OWNER TO shardeya_statsrefresh;
ALTER MATERIALIZED VIEW mv_broker_performance OWNER TO shardeya_statsrefresh;
ALTER MATERIALIZED VIEW mv_staff_activity OWNER TO shardeya_statsrefresh;

-- Ownership transfer doesn't revoke previously-granted privileges to OTHER
-- roles, but re-granting explicitly costs nothing and matches this
-- project's own stated "cheap to be explicit, expensive to silently rely
-- on it still being there" philosophy (V7_008's identical comment).
GRANT SELECT ON mv_monthly_sales, mv_lead_funnel, mv_broker_performance, mv_staff_activity TO shardeya_app;
