-- M-05 Media & Document Storage. Standard bucket only this milestone —
-- the sensitive bucket (gov-ID scans, SSE-KMS, 5-min presign) is B-04/M3
-- scope (01-DATA-MODEL.md §9 media_asset, 00-ARCHITECTURE.md §4.10).
CREATE TYPE media_bucket_class AS ENUM ('STANDARD', 'SENSITIVE');
CREATE TYPE media_status AS ENUM ('PENDING', 'SCANNING', 'READY', 'REJECTED');

CREATE TABLE media_asset (
    id                 UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    org_id             UUID NOT NULL REFERENCES organization(id),
    storage_key        TEXT NOT NULL,
    bucket_class       media_bucket_class NOT NULL DEFAULT 'STANDARD',
    original_filename  VARCHAR(255) NOT NULL,
    mime_type          VARCHAR(100) NOT NULL,
    size_bytes         BIGINT NOT NULL,
    checksum_sha256    VARCHAR(64),
    width              INTEGER,
    height             INTEGER,
    duration_seconds   INTEGER,
    status             media_status NOT NULL DEFAULT 'PENDING',
    reject_reason      VARCHAR(255),
    uploaded_by        UUID REFERENCES app_user(id),
    derivatives        JSONB,
    created_at         TIMESTAMPTZ NOT NULL DEFAULT now(),
    created_by         UUID REFERENCES app_user(id),
    updated_at         TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_by         UUID REFERENCES app_user(id),
    deleted_at         TIMESTAMPTZ,
    deleted_by         UUID REFERENCES app_user(id),
    version            BIGINT NOT NULL DEFAULT 0
);

CREATE INDEX ix_media_asset_org ON media_asset(org_id) WHERE deleted_at IS NULL;
-- Orphan reaping (M-05: PENDING >24h with no parent gets purged) needs to
-- find stale PENDING rows cheaply, org-agnostic (it's a background sweep).
CREATE INDEX ix_media_asset_pending ON media_asset(status, created_at) WHERE status = 'PENDING';

ALTER TABLE media_asset ENABLE ROW LEVEL SECURITY;
ALTER TABLE media_asset FORCE ROW LEVEL SECURITY;
CREATE POLICY tenant_isolation ON media_asset
    USING (org_id = NULLIF(current_setting('app.current_org', true), '')::uuid);
