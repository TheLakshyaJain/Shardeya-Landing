-- M-07 Import Engine (01-DATA-MODEL.md §11 import_job). Only PLOT entity_type
-- is exercised this milestone (B-06); PROPERTY/CUSTOMER importers are later.
CREATE TYPE import_entity_type AS ENUM ('PROPERTY', 'PLOT', 'CUSTOMER');
CREATE TYPE import_job_status AS ENUM ('UPLOADED', 'VALIDATING', 'PREVIEW_READY', 'IMPORTING', 'COMPLETED', 'FAILED', 'CANCELLED');

CREATE TABLE import_job (
    id                     UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    org_id                 UUID NOT NULL REFERENCES organization(id),
    entity_type            import_entity_type NOT NULL,
    target_project_id      UUID REFERENCES project(id),
    source_media_id        UUID REFERENCES media_asset(id),
    template_version       VARCHAR(20) NOT NULL,
    status                 import_job_status NOT NULL DEFAULT 'UPLOADED',
    total_rows             INTEGER NOT NULL DEFAULT 0,
    valid_rows             INTEGER NOT NULL DEFAULT 0,
    invalid_rows           INTEGER NOT NULL DEFAULT 0,
    imported_rows          INTEGER NOT NULL DEFAULT 0,
    started_by             UUID REFERENCES app_user(id),
    started_at             TIMESTAMPTZ,
    completed_at           TIMESTAMPTZ,
    error_report_media_id  UUID REFERENCES media_asset(id),
    created_at             TIMESTAMPTZ NOT NULL DEFAULT now(),
    created_by             UUID REFERENCES app_user(id),
    updated_at             TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_by             UUID REFERENCES app_user(id),
    deleted_at             TIMESTAMPTZ,
    deleted_by             UUID REFERENCES app_user(id),
    version                BIGINT NOT NULL DEFAULT 0
);

CREATE INDEX ix_import_job_org ON import_job(org_id) WHERE deleted_at IS NULL;
CREATE INDEX ix_import_job_project ON import_job(target_project_id) WHERE deleted_at IS NULL;

ALTER TABLE import_job ENABLE ROW LEVEL SECURITY;
ALTER TABLE import_job FORCE ROW LEVEL SECURITY;
CREATE POLICY tenant_isolation ON import_job
    USING (org_id = NULLIF(current_setting('app.current_org', true), '')::uuid);
