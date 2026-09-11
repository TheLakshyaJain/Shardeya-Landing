-- The role that runs migrations (DB_USER, e.g. the docker-compose Postgres
-- bootstrap user) is a superuser, and superusers always bypass Row-Level
-- Security — FORCE ROW LEVEL SECURITY cannot override that (only owner-bypass).
-- The application must therefore run as a *different*, ordinary role for RLS to
-- mean anything. This role owns nothing and has no BYPASSRLS/SUPERUSER, so
-- ENABLE ROW LEVEL SECURITY alone is enough to constrain it.
DO $$
BEGIN
    IF NOT EXISTS (SELECT FROM pg_roles WHERE rolname = 'shardeya_app') THEN
        CREATE ROLE shardeya_app LOGIN PASSWORD 'shardeya_app';
    END IF;
END
$$;

GRANT USAGE ON SCHEMA public TO shardeya_app;
GRANT SELECT, INSERT, UPDATE, DELETE ON ALL TABLES IN SCHEMA public TO shardeya_app;
ALTER DEFAULT PRIVILEGES IN SCHEMA public
    GRANT SELECT, INSERT, UPDATE, DELETE ON TABLES TO shardeya_app;
