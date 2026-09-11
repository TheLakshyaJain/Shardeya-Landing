-- Caught live: StatsRefreshJob's first real scheduled tick failed with
-- "must be owner of materialized view mv_monthly_sales". Unlike ordinary
-- tables, REFRESH MATERIALIZED VIEW has no GRANT-based escape hatch at
-- all -- it requires actual ownership (or superuser), full stop, no
-- privilege short of ownership makes the SELECT/INSERT/UPDATE/DELETE
-- GRANTs V0_005/V7_008 already gave shardeya_app relevant. This is the same
-- "migrations run as the owning superuser role, the app runs as the
-- ordinary shardeya_app role" split M0 already established for RLS
-- owner-bypass, showing up in a different Postgres corner. Safe to
-- transfer ownership outright -- matviews have no RLS to weaken (Postgres
-- doesn't support RLS on them at all, see V7_008's own comment); isolation
-- here is entirely the explicit `WHERE org_id = ...` predicate in every
-- StatsService query, unaffected by who owns the underlying view.
ALTER MATERIALIZED VIEW mv_monthly_sales OWNER TO shardeya_app;
ALTER MATERIALIZED VIEW mv_lead_funnel OWNER TO shardeya_app;
ALTER MATERIALIZED VIEW mv_broker_performance OWNER TO shardeya_app;
ALTER MATERIALIZED VIEW mv_staff_activity OWNER TO shardeya_app;
