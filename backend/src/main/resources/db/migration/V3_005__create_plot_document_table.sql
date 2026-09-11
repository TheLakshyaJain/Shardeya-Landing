-- B-04 §7/§12.3.3: four typed slots (Sale Agreement, Registry/Deed, Plot Map,
-- Buyer ID Proof) plus multi-file "Other" with a required label. ID Proof is
-- auto-classified sensitive (is_sensitive) and routed to the sensitive S3
-- bucket by MediaService, never the standard media pipeline.
CREATE TYPE plot_document_type AS ENUM ('SALE_AGREEMENT', 'REGISTRY_DEED', 'PLOT_MAP', 'BUYER_ID_PROOF', 'OTHER');

CREATE TABLE plot_document (
    id             UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    org_id         UUID NOT NULL REFERENCES organization(id),
    plot_id        UUID NOT NULL REFERENCES plot(id),
    plot_sale_id   UUID NOT NULL REFERENCES plot_sale(id),
    doc_type       plot_document_type NOT NULL,
    label          VARCHAR(120),
    media_id       UUID NOT NULL,
    is_sensitive   BOOLEAN NOT NULL DEFAULT false,
    created_at     TIMESTAMPTZ NOT NULL DEFAULT now(),
    created_by     UUID REFERENCES app_user(id),
    updated_at     TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_by     UUID REFERENCES app_user(id),
    deleted_at     TIMESTAMPTZ,
    deleted_by     UUID REFERENCES app_user(id),
    version        BIGINT NOT NULL DEFAULT 0
);

CREATE INDEX ix_plot_document_org ON plot_document(org_id) WHERE deleted_at IS NULL;
CREATE INDEX ix_plot_document_sale ON plot_document(plot_sale_id) WHERE deleted_at IS NULL;

ALTER TABLE plot_document ENABLE ROW LEVEL SECURITY;
ALTER TABLE plot_document FORCE ROW LEVEL SECURITY;
CREATE POLICY tenant_isolation ON plot_document
    USING (org_id = NULLIF(current_setting('app.current_org', true), '')::uuid);
