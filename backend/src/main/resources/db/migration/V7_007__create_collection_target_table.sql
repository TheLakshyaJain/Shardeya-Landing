-- B-15 §3: optional per-project monthly collection target, making the
-- "Collection vs Target" chart real. No target-setting UI ships this round
-- (see CLAUDE.md) -- StatsService's fallback (SUM(payment_schedule.expected_amount)
-- due that month, B-15 §7's own "meaningful default, requires no setup") is
-- what every chart exercises; this table exists so a future target-setting
-- screen has somewhere to write.
CREATE TABLE collection_target (
    org_id          UUID NOT NULL REFERENCES organization(id),
    project_id      UUID NOT NULL REFERENCES project(id),
    month           DATE NOT NULL,
    target_amount   NUMERIC(19,2) NOT NULL CHECK (target_amount >= 0),
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    PRIMARY KEY (org_id, project_id, month)
);

ALTER TABLE collection_target ENABLE ROW LEVEL SECURITY;
ALTER TABLE collection_target FORCE ROW LEVEL SECURITY;
CREATE POLICY tenant_isolation ON collection_target
    USING (org_id = NULLIF(current_setting('app.current_org', true), '')::uuid);
