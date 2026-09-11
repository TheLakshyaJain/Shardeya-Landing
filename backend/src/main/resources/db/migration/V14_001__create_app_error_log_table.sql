-- Deliberately scoped-down stand-in for M-14 (Platform Admin Console) --
-- see CLAUDE.md's own "Post-M7 -- Minimal Ops Visibility" notes for why the
-- full M-14 (platform_admin realm, org suspension, feature flags, rate
-- editor, etc.) is not being built yet. This is the operational half only:
-- "would we find out before the customer does."
--
-- org_id is nullable on purpose -- a genuine server error can happen before
-- any tenant context is ever bound (a malformed request to /auth/login, a
-- background job with no single org to attribute it to). A row with
-- org_id IS NULL is a platform-level error, visible to every admin; a row
-- with a real org_id is that org's own error, visible only to that org's
-- own admin -- same nullable-org_id RLS shape `role.org_id` already
-- established (V0_002), just with the safer missing_ok current_setting
-- read `message_delivery` (V7_015) already uses, since this table's own
-- INSERT (from GlobalExceptionHandler) must never itself throw just
-- because no tenant happens to be bound yet.
CREATE TABLE app_error_log (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    org_id          UUID REFERENCES organization(id),
    user_id         UUID,
    http_method     VARCHAR(10) NOT NULL,
    path            VARCHAR(500) NOT NULL,
    exception_class VARCHAR(300) NOT NULL,
    message         TEXT,
    occurred_at     TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX ix_app_error_log_org_occurred ON app_error_log(org_id, occurred_at DESC);
CREATE INDEX ix_app_error_log_occurred ON app_error_log(occurred_at DESC);

ALTER TABLE app_error_log ENABLE ROW LEVEL SECURITY;
ALTER TABLE app_error_log FORCE ROW LEVEL SECURITY;
CREATE POLICY tenant_isolation ON app_error_log
    USING (org_id IS NULL OR org_id = NULLIF(current_setting('app.current_org', true), '')::uuid);
