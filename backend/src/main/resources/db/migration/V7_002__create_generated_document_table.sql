-- B-11 §17.2 / 01-DATA-MODEL.md §9 generated_document. Deliberately no
-- [STD] soft-delete/version columns -- the data model's own field list for
-- this table omits them (matching payment_record's "immutable, no delete"
-- shape: a generated PDF is a historical record, never edited in place).
-- rendered_snapshot is the JSONB proof that reprinting later reproduces the
-- original figures even if the template or the underlying sale changed
-- since (B-11 §11 "template edited after documents were generated -> old
-- documents unchanged").
CREATE TABLE generated_document (
    id                  UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    org_id              UUID NOT NULL REFERENCES organization(id),
    doc_type            document_type_code NOT NULL,
    template_id         UUID NOT NULL REFERENCES document_template(id),
    template_version    BIGINT NOT NULL,
    entity_type         VARCHAR(30) NOT NULL,
    entity_id           UUID NOT NULL,
    media_id            UUID NOT NULL REFERENCES media_asset(id),
    rendered_snapshot    JSONB NOT NULL,
    document_number     VARCHAR(40) NOT NULL,
    language            VARCHAR(2) NOT NULL,
    generated_by        UUID NOT NULL REFERENCES app_user(id),
    generated_at        TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT ck_generated_document_language CHECK (language IN ('en', 'hi'))
);

CREATE INDEX ix_generated_document_org ON generated_document(org_id);
CREATE INDEX ix_generated_document_entity ON generated_document(org_id, entity_type, entity_id);
CREATE UNIQUE INDEX ux_generated_document_number ON generated_document(org_id, doc_type, document_number);

ALTER TABLE generated_document ENABLE ROW LEVEL SECURITY;
ALTER TABLE generated_document FORCE ROW LEVEL SECURITY;
CREATE POLICY tenant_isolation ON generated_document
    USING (org_id = NULLIF(current_setting('app.current_org', true), '')::uuid);
