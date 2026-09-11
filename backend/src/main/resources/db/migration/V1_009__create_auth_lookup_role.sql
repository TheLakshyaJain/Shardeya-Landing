-- app_user's login identifiers (mobile, email) are deliberately GLOBAL, not
-- per-org (V0_003 / M0 decision: "a person is one login"). That means the
-- login/signup flow must look a user up by mobile-or-email *before* it knows
-- which org they belong to — but app_user's RLS policy requires org_id to
-- already be set. Making the policy fail-open when app.current_org is unset
-- ("if no tenant context, show everything") would silently defeat the entire
-- point of RLS as a backstop for a forgotten SET, so that's not an option.
--
-- Instead: a third, narrowly-scoped role, used by exactly one code path (the
-- pre-authentication identifier lookup in AuthService) via its own dedicated
-- datasource — never the general JPA/Hibernate datasource. Everything else
-- (loading *your own* profile once authenticated, editing it, etc.) still
-- goes through shardeya_app with RLS fully enforced as normal.
DO $$
BEGIN
    IF NOT EXISTS (SELECT FROM pg_roles WHERE rolname = 'shardeya_authlookup') THEN
        CREATE ROLE shardeya_authlookup LOGIN PASSWORD 'shardeya_authlookup' BYPASSRLS;
    END IF;
END
$$;

GRANT USAGE ON SCHEMA public TO shardeya_authlookup;
GRANT SELECT ON app_user TO shardeya_authlookup;
-- Joined in the same lookup query to get the role code for JWT issuance
-- without a second round trip; role rows carry no sensitive data.
GRANT SELECT ON role TO shardeya_authlookup;
