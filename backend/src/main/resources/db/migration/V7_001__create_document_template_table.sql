-- B-11 §17.2 / 01-DATA-MODEL.md §9 document_template. org_id IS nullable
-- (NULL = system default, seeded once per doc_type+language, visible to
-- every org) -- same "org_id IS NULL OR org_id = current org" RLS shape
-- M0's `role.org_id` already established for exactly this "system row
-- visible to every tenant, but tenant-owned rows stay isolated" case.
--
-- `version` doubles as both JPA optimistic-lock AND the business
-- "template_version" recorded onto generated_document.template_version at
-- generation time -- there is deliberately no separate version-history
-- table; incrementing on every edit is exactly what's needed to answer
-- "which revision of this template produced that PDF".
CREATE TYPE document_type_code AS ENUM ('ALLOTMENT_LETTER', 'PAYMENT_RECEIPT', 'DEMAND_LETTER', 'BOOKING_CONFIRMATION');

CREATE TABLE document_template (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    org_id          UUID REFERENCES organization(id),
    doc_type        document_type_code NOT NULL,
    name            VARCHAR(120) NOT NULL,
    language        VARCHAR(2) NOT NULL,
    body_html       TEXT NOT NULL,
    header_html     TEXT,
    footer_html     TEXT,
    variables       JSONB NOT NULL DEFAULT '[]',
    version         BIGINT NOT NULL DEFAULT 0,
    is_active       BOOLEAN NOT NULL DEFAULT false,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    created_by      UUID REFERENCES app_user(id),
    updated_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_by      UUID REFERENCES app_user(id),
    deleted_at      TIMESTAMPTZ,
    deleted_by      UUID REFERENCES app_user(id),
    CONSTRAINT ck_document_template_language CHECK (language IN ('en', 'hi')),
    CONSTRAINT ck_document_template_body_size CHECK (octet_length(body_html) <= 204800),
    CONSTRAINT ck_document_template_name_len CHECK (char_length(name) BETWEEN 2 AND 120)
);

CREATE INDEX ix_document_template_org ON document_template(org_id) WHERE deleted_at IS NULL;
CREATE INDEX ix_document_template_lookup ON document_template(org_id, doc_type, language) WHERE deleted_at IS NULL;

-- B-11 §11: "unique per org per type per language". NULLS NOT DISTINCT
-- (PG15+) so this also protects the org_id-NULL system-default rows from
-- ever colliding with each other, not just org-owned rows.
CREATE UNIQUE INDEX ux_document_template_name
    ON document_template(org_id, doc_type, language, name) NULLS NOT DISTINCT WHERE deleted_at IS NULL;

-- At most one ACTIVE template per (org-or-system, doc_type, language) --
-- DocumentTemplateService.activate() deactivates the previous one in the
-- same transaction; this is the backstop, not the primary mechanism.
CREATE UNIQUE INDEX ux_document_template_active
    ON document_template(org_id, doc_type, language) NULLS NOT DISTINCT WHERE is_active AND deleted_at IS NULL;

ALTER TABLE document_template ENABLE ROW LEVEL SECURITY;
ALTER TABLE document_template FORCE ROW LEVEL SECURITY;
CREATE POLICY tenant_isolation ON document_template
    USING (org_id IS NULL OR org_id = NULLIF(current_setting('app.current_org', true), '')::uuid);
