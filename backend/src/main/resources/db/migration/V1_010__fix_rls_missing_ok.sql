-- 01-DATA-MODEL.md's original RLS template used
-- current_setting('app.current_org') without the missing_ok=true second
-- argument. On a connection where the GUC was never SET at all (not even
-- RESET to empty — genuinely never touched), that call raises "unrecognized
-- configuration parameter" instead of returning NULL. That's not a
-- theoretical edge case: signup must look up the BUILDER_ADMIN/BROKER_OWNER
-- *system* role (org_id IS NULL) before any organization exists and before
-- the request has any tenant context to set — exactly the scenario that
-- trips this. Discovered while wiring RefreshTokenServiceTest's fixtures.
--
-- missing_ok=true alone is still not enough, though: TenantAwareDataSource
-- runs `RESET app.current_org` for unauthenticated requests, and RESET on a
-- custom (undeclared) GUC sets it to an EMPTY STRING, not NULL. So
-- current_setting(..., true) can legitimately return '', and ''::uuid then
-- fails with "invalid input syntax for type uuid" — a second, different
-- error hiding behind the first fix. NULLIF(..., '') collapses both "never
-- set" (NULL) and "explicitly reset" ('') to the same NULL before the cast,
-- which is what every USING clause below actually wants: "no tenant context"
-- should read as NULL, full stop, regardless of which of the two ways it
-- ended up unset.
ALTER POLICY tenant_isolation ON role
    USING (org_id IS NULL OR org_id = NULLIF(current_setting('app.current_org', true), '')::uuid);

ALTER POLICY tenant_isolation ON app_user
    USING (org_id = NULLIF(current_setting('app.current_org', true), '')::uuid);

ALTER POLICY tenant_isolation ON subscription
    USING (org_id = NULLIF(current_setting('app.current_org', true), '')::uuid);
