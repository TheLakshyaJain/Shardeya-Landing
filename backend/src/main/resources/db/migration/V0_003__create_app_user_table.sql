CREATE TYPE project_access_mode AS ENUM ('ALL', 'SCOPED');
CREATE TYPE app_user_status AS ENUM ('INVITED', 'ACTIVE', 'INACTIVE', 'REMOVED');

CREATE TABLE app_user (
    id                    UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    org_id                UUID NOT NULL REFERENCES organization(id),
    full_name             VARCHAR(100) NOT NULL,
    mobile                VARCHAR(10) NOT NULL,
    mobile_verified_at    TIMESTAMPTZ,
    email                 VARCHAR(255),
    email_verified_at     TIMESTAMPTZ,
    password_hash         TEXT,
    role_id               UUID NOT NULL REFERENCES role(id),
    is_owner              BOOLEAN NOT NULL DEFAULT false,
    project_access_mode   project_access_mode NOT NULL DEFAULT 'ALL',
    language              CHAR(2) NOT NULL DEFAULT 'en',
    status                app_user_status NOT NULL DEFAULT 'INVITED',
    last_login_at         TIMESTAMPTZ,
    failed_login_count    SMALLINT NOT NULL DEFAULT 0,
    locked_until          TIMESTAMPTZ,
    created_at            TIMESTAMPTZ NOT NULL DEFAULT now(),
    created_by            UUID REFERENCES app_user(id),
    updated_at            TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_by            UUID REFERENCES app_user(id),
    deleted_at            TIMESTAMPTZ,
    deleted_by            UUID REFERENCES app_user(id),
    version               BIGINT NOT NULL DEFAULT 0
);

CREATE INDEX ix_app_user_org ON app_user(org_id) WHERE deleted_at IS NULL;
CREATE UNIQUE INDEX ux_user_mobile ON app_user(mobile) WHERE deleted_at IS NULL;
CREATE UNIQUE INDEX ux_user_email ON app_user(lower(email)) WHERE email IS NOT NULL AND deleted_at IS NULL;
CREATE UNIQUE INDEX ux_org_one_owner ON app_user(org_id) WHERE is_owner AND deleted_at IS NULL;

ALTER TABLE app_user ENABLE ROW LEVEL SECURITY;
-- FORCE: see V0_002 — without it, RLS is silently bypassed for the table owner,
-- which is also the application's runtime role in this setup.
ALTER TABLE app_user FORCE ROW LEVEL SECURITY;

CREATE POLICY tenant_isolation ON app_user
    USING (org_id = current_setting('app.current_org')::uuid);
