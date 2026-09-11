-- B-05 §7: "The allocation table is the key design decision." One receipt
-- may settle part of one instalment or span several; this join table is
-- what makes partial-payment balances answerable at all.
CREATE TABLE payment_allocation (
    id                    UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    org_id                UUID NOT NULL REFERENCES organization(id),
    payment_record_id     UUID NOT NULL REFERENCES payment_record(id),
    payment_schedule_id   UUID NOT NULL REFERENCES payment_schedule(id),
    amount                NUMERIC(19,2) NOT NULL CHECK (amount <> 0),
    created_at            TIMESTAMPTZ NOT NULL DEFAULT now(),
    created_by            UUID REFERENCES app_user(id),
    deleted_at            TIMESTAMPTZ,
    deleted_by            UUID REFERENCES app_user(id)
);

CREATE INDEX ix_payment_allocation_org ON payment_allocation(org_id) WHERE deleted_at IS NULL;
CREATE INDEX ix_payment_allocation_record ON payment_allocation(payment_record_id) WHERE deleted_at IS NULL;
CREATE INDEX ix_payment_allocation_schedule ON payment_allocation(payment_schedule_id) WHERE deleted_at IS NULL;

ALTER TABLE payment_allocation ENABLE ROW LEVEL SECURITY;
ALTER TABLE payment_allocation FORCE ROW LEVEL SECURITY;
CREATE POLICY tenant_isolation ON payment_allocation
    USING (org_id = NULLIF(current_setting('app.current_org', true), '')::uuid);
