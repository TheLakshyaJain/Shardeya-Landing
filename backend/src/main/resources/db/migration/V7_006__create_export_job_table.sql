-- M-10 §3 export_job. Every export writes a row here (filter_snapshot is
-- the audit trail answering "what filters produced this file") even though
-- this milestone's realistic data volumes never cross the 5,000-row async
-- threshold M-10 §7 describes -- ReportService always takes the synchronous
-- path and completes the job inline in the same request rather than a
-- background poller; see CLAUDE.md for why a full async job runner was
-- deliberately trimmed this round.
CREATE TABLE export_job (
    id                UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    org_id            UUID NOT NULL REFERENCES organization(id),
    entity_type       VARCHAR(40) NOT NULL,
    format            VARCHAR(10) NOT NULL,
    filter_snapshot   JSONB NOT NULL DEFAULT '{}',
    scope             VARCHAR(10) NOT NULL DEFAULT 'FILTERED',
    status            VARCHAR(15) NOT NULL DEFAULT 'PENDING',
    row_count         INTEGER,
    result_media_id   UUID REFERENCES media_asset(id),
    error_message     TEXT,
    expires_at        TIMESTAMPTZ,
    requested_by      UUID NOT NULL REFERENCES app_user(id),
    created_at        TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at        TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT ck_export_job_format CHECK (format IN ('XLSX', 'CSV', 'PDF')),
    CONSTRAINT ck_export_job_scope CHECK (scope IN ('ALL', 'FILTERED')),
    CONSTRAINT ck_export_job_status CHECK (status IN ('PENDING', 'PROCESSING', 'COMPLETED', 'FAILED'))
);

CREATE INDEX ix_export_job_org ON export_job(org_id, created_at DESC);

ALTER TABLE export_job ENABLE ROW LEVEL SECURITY;
ALTER TABLE export_job FORCE ROW LEVEL SECURITY;
CREATE POLICY tenant_isolation ON export_job
    USING (org_id = NULLIF(current_setting('app.current_org', true), '')::uuid);
