-- B-05 §3: gapless receipt numbering per (org, financial year). The row is
-- locked with SELECT ... FOR UPDATE inside the same transaction as the
-- payment_record insert (ReceiptService), so a crash between "read next_value"
-- and "insert payment_record" simply rolls back both together -- no gap, no
-- duplicate. FY is computed from paid_on (the receipt's own date), never
-- now() -- B-05 §10 explicitly calls out FY-rollover-mid-transaction as an
-- edge case this must handle correctly.
CREATE TABLE receipt_sequence (
    org_id      UUID NOT NULL REFERENCES organization(id),
    fy          VARCHAR(7) NOT NULL,
    next_value  BIGINT NOT NULL DEFAULT 1,
    PRIMARY KEY (org_id, fy)
);

ALTER TABLE receipt_sequence ENABLE ROW LEVEL SECURITY;
ALTER TABLE receipt_sequence FORCE ROW LEVEL SECURITY;
CREATE POLICY tenant_isolation ON receipt_sequence
    USING (org_id = NULLIF(current_setting('app.current_org', true), '')::uuid);
