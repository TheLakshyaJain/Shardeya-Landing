-- B-02 Manage Projects (01-DATA-MODEL.md §4 project / §12.2.1).
CREATE TYPE project_type AS ENUM ('RESIDENTIAL_PLOT_COLONY', 'APARTMENT', 'VILLA', 'COMMERCIAL', 'MIXED_USE');
CREATE TYPE project_status AS ENUM ('UPCOMING', 'ACTIVE', 'COMPLETED');

CREATE TABLE project (
    id                         UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    org_id                     UUID NOT NULL REFERENCES organization(id),
    name                       VARCHAR(150) NOT NULL,
    project_type               project_type NOT NULL,
    status                     project_status NOT NULL DEFAULT 'UPCOMING',
    address                    TEXT NOT NULL,
    locality                   VARCHAR(150) NOT NULL,
    city                       VARCHAR(100) NOT NULL,
    -- VARCHAR, not CHAR: Hibernate maps a plain String field to varchar by
    -- default, and a CHAR(n) column fails schema validation at startup
    -- ("wrong column type... found bpchar, expecting varchar") — the exact
    -- bug V1_008 already fixed once for app_user/organization in M1. Every
    -- new fixed-length-looking code column goes VARCHAR from the start now.
    state_code                 VARCHAR(2) NOT NULL,
    pincode                    VARCHAR(6),
    google_maps_url            TEXT,
    total_area_value           NUMERIC(14,4) NOT NULL,
    total_area_unit            VARCHAR(16) NOT NULL,
    total_area_sqft            NUMERIC(14,4) NOT NULL,
    declared_plot_count        INTEGER NOT NULL,
    launch_date                DATE,
    expected_completion_date   DATE,
    description                TEXT,
    approvals                  JSONB NOT NULL DEFAULT '[]',
    rera_number                VARCHAR(60),
    cover_media_id             UUID REFERENCES media_asset(id),
    layout_media_id            UUID REFERENCES media_asset(id),
    brochure_media_id          UUID REFERENCES media_asset(id),
    grid_rows                  INTEGER,
    grid_cols                  INTEGER,
    grid_layout                JSONB NOT NULL DEFAULT '{}',
    created_at                 TIMESTAMPTZ NOT NULL DEFAULT now(),
    created_by                 UUID REFERENCES app_user(id),
    updated_at                 TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_by                 UUID REFERENCES app_user(id),
    deleted_at                 TIMESTAMPTZ,
    deleted_by                 UUID REFERENCES app_user(id),
    version                    BIGINT NOT NULL DEFAULT 0
);

CREATE INDEX ix_project_org ON project(org_id) WHERE deleted_at IS NULL;
CREATE INDEX ix_project_status ON project(org_id, status) WHERE deleted_at IS NULL;
CREATE UNIQUE INDEX ux_project_name ON project(org_id, lower(name)) WHERE deleted_at IS NULL;

ALTER TABLE project ENABLE ROW LEVEL SECURITY;
ALTER TABLE project FORCE ROW LEVEL SECURITY;
CREATE POLICY tenant_isolation ON project
    USING (org_id = NULLIF(current_setting('app.current_org', true), '')::uuid);
