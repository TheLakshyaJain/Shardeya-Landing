-- M-09 org_usage (01-DATA-MODEL.md §10 / §13 trigger contract) — the
-- "never COUNT(*)" read model for quota checks.
--
-- Judgment call: the doc's schema is `org_id, limit_key, current_value` with
-- no project scoping, but BUILDER_PLOTS_PER_PROJECT is explicitly "per
-- project, not org-wide" (M-09 business logic) — a Pro builder can have 5
-- projects x 500 plots. A plain (org_id, limit_key) row can't represent
-- that. Added a nullable `scope_id` (NULL for org-wide limits like
-- BUILDER_PROJECTS; the project's id for BUILDER_PLOTS_PER_PROJECT).
-- PRIMARY KEY can't include a nullable column, so `id` is the real surrogate
-- key and a UNIQUE INDEX on (org_id, limit_key, coalesce(scope_id, org_id))
-- enforces the intended "one row per (org, limit, scope)" — using org_id
-- itself as the "no scope" sentinel is safe since scope_id only ever holds
-- project ids, a disjoint UUID population.
CREATE TABLE org_usage (
    id             UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    org_id         UUID NOT NULL REFERENCES organization(id),
    limit_key      VARCHAR(40) NOT NULL,
    scope_id       UUID,
    current_value  INTEGER NOT NULL DEFAULT 0,
    updated_at     TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE UNIQUE INDEX ux_org_usage_scope ON org_usage(org_id, limit_key, (coalesce(scope_id, org_id)));
CREATE INDEX ix_org_usage_org ON org_usage(org_id);

ALTER TABLE org_usage ENABLE ROW LEVEL SECURITY;
ALTER TABLE org_usage FORCE ROW LEVEL SECURITY;
CREATE POLICY tenant_isolation ON org_usage
    USING (org_id = NULLIF(current_setting('app.current_org', true), '')::uuid);
