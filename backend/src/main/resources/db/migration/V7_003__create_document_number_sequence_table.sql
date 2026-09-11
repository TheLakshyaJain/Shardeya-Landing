-- B-11 §3: gapless per org, per doc type, per financial year. Exact same
-- shape as B-05's own receipt_sequence (V3_006) with a doc_type dimension
-- added -- PAYMENT_RECEIPT documents reuse payment_record's own already-
-- gapless receipt_no directly rather than drawing a second, independent
-- number from this table (see DocumentNumberService javadoc); this table
-- only numbers ALLOTMENT_LETTER / DEMAND_LETTER / BOOKING_CONFIRMATION.
CREATE TABLE document_number_sequence (
    org_id       UUID NOT NULL REFERENCES organization(id),
    doc_type     document_type_code NOT NULL,
    fy           VARCHAR(7) NOT NULL,
    next_value   BIGINT NOT NULL DEFAULT 1,
    PRIMARY KEY (org_id, doc_type, fy)
);

ALTER TABLE document_number_sequence ENABLE ROW LEVEL SECURITY;
ALTER TABLE document_number_sequence FORCE ROW LEVEL SECURITY;
CREATE POLICY tenant_isolation ON document_number_sequence
    USING (org_id = NULLIF(current_setting('app.current_org', true), '')::uuid);
