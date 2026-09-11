CREATE TABLE role (
    id         UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    org_id     UUID REFERENCES organization(id),
    code       VARCHAR(40) NOT NULL,
    name_en    VARCHAR(60) NOT NULL,
    name_hi    VARCHAR(60) NOT NULL,
    is_system  BOOLEAN NOT NULL DEFAULT false
);

CREATE UNIQUE INDEX ux_role_system_code ON role(code) WHERE org_id IS NULL;
CREATE INDEX ix_role_org ON role(org_id) WHERE org_id IS NOT NULL;

ALTER TABLE role ENABLE ROW LEVEL SECURITY;
-- FORCE is required because the application connects as the same role that owns
-- the tables (see application.yml DB_USER) — Postgres exempts table owners from
-- RLS by default, which would make this policy a no-op backstop that silently
-- never fires. See CLAUDE.md "Milestone 0 — Decisions & Environment Notes".
ALTER TABLE role FORCE ROW LEVEL SECURITY;

-- System roles (org_id IS NULL) are visible to every tenant; tenant-custom roles
-- (future) are visible only to their own org.
CREATE POLICY tenant_isolation ON role
    USING (org_id IS NULL OR org_id = current_setting('app.current_org')::uuid);
