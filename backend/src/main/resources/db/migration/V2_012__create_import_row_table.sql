-- import_row has no org_id/RLS of its own — scoped transitively through
-- import_job_id, same pattern as role_permission (01-DATA-MODEL.md §11).
CREATE TYPE import_row_status AS ENUM ('VALID', 'INVALID', 'IMPORTED', 'SKIPPED');

CREATE TABLE import_row (
    id                 UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    import_job_id      UUID NOT NULL REFERENCES import_job(id),
    row_number         INTEGER NOT NULL,
    raw_data           JSONB NOT NULL,
    normalised_data    JSONB,
    status             import_row_status NOT NULL DEFAULT 'INVALID',
    errors             JSONB NOT NULL DEFAULT '[]',
    created_entity_id  UUID
);

CREATE INDEX ix_import_row_job ON import_row(import_job_id, row_number);
CREATE INDEX ix_import_row_job_status ON import_row(import_job_id, status);
