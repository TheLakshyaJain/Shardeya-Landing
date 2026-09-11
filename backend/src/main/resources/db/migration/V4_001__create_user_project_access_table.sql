-- M-02 §3 / B-12 §18.2: rows only exist when app_user.project_access_mode =
-- 'SCOPED'. A user with project_access_mode = 'ALL' has no rows here at all
-- -- ProjectAccessGuard checks the mode first and only consults this table
-- for SCOPED users (see that class).
CREATE TABLE user_project_access (
    id          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    org_id      UUID NOT NULL REFERENCES organization(id),
    user_id     UUID NOT NULL REFERENCES app_user(id),
    project_id  UUID NOT NULL REFERENCES project(id),
    created_at  TIMESTAMPTZ NOT NULL DEFAULT now(),
    created_by  UUID REFERENCES app_user(id)
);

CREATE UNIQUE INDEX ux_user_project_access ON user_project_access(user_id, project_id);
CREATE INDEX ix_user_project_access_org ON user_project_access(org_id);
CREATE INDEX ix_user_project_access_user ON user_project_access(user_id);

ALTER TABLE user_project_access ENABLE ROW LEVEL SECURITY;
ALTER TABLE user_project_access FORCE ROW LEVEL SECURITY;
CREATE POLICY tenant_isolation ON user_project_access
    USING (org_id = NULLIF(current_setting('app.current_org', true), '')::uuid);
