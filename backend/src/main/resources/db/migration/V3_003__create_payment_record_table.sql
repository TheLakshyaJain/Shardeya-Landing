-- B-05 Payment Tracker (01-DATA-MODEL.md §4 payment_record / §12.3.4, §14.2).
-- Immutable -- CLAUDE.md rule #7: "Never delete financial records. Reversals
-- only." A negative-amount row referencing reverses_payment_id is the only
-- correction path; deletion is blocked outright by a RULE (same pattern the
-- data model already specifies for `interaction`, applied here since this
-- is the actual money ledger it was written for).
CREATE TYPE payment_mode AS ENUM ('CASH', 'CHEQUE', 'BANK_TRANSFER', 'UPI', 'DD');
CREATE TYPE cheque_status AS ENUM ('PENDING', 'CLEARED', 'BOUNCED');

CREATE TABLE payment_record (
    id                     UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    org_id                 UUID NOT NULL REFERENCES organization(id),
    plot_sale_id           UUID NOT NULL REFERENCES plot_sale(id),
    project_id             UUID NOT NULL REFERENCES project(id),
    plot_id                UUID NOT NULL REFERENCES plot(id),
    -- Generated: {ORG_PREFIX}/{FY}/{seq} -- gapless per org per FY, allocated
    -- via receipt_sequence (V3_006) under SELECT ... FOR UPDATE inside the
    -- same transaction as this insert.
    receipt_no             VARCHAR(30) NOT NULL,
    amount                 NUMERIC(19,2) NOT NULL CHECK (amount <> 0),
    paid_on                DATE NOT NULL,
    mode                   payment_mode NOT NULL,
    reference               VARCHAR(120),
    cheque_status            cheque_status,
    received_by              UUID NOT NULL REFERENCES app_user(id),
    remarks                  TEXT,
    reverses_payment_id      UUID REFERENCES payment_record(id),
    -- No FK: generated_document (B-11) doesn't exist yet -- receipt PDF
    -- generation is a stub this milestone (05-MILESTONES.md M3: "full PDF in
    -- M5"). Add the FK when B-11 lands.
    receipt_document_id      UUID,
    created_at                TIMESTAMPTZ NOT NULL DEFAULT now(),
    created_by                UUID REFERENCES app_user(id),
    updated_at                TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_by                UUID REFERENCES app_user(id),
    version                    BIGINT NOT NULL DEFAULT 0
    -- Deliberately NO deleted_at -- see the RULE below instead.
);

CREATE INDEX ix_payment_record_org ON payment_record(org_id);
CREATE UNIQUE INDEX ux_payment_receipt_no ON payment_record(org_id, receipt_no);
CREATE INDEX ix_payment_record_sale ON payment_record(plot_sale_id);

CREATE RULE no_delete_payment_record AS ON DELETE TO payment_record DO INSTEAD NOTHING;

ALTER TABLE payment_record ENABLE ROW LEVEL SECURITY;
ALTER TABLE payment_record FORCE ROW LEVEL SECURITY;
CREATE POLICY tenant_isolation ON payment_record
    USING (org_id = NULLIF(current_setting('app.current_org', true), '')::uuid);
