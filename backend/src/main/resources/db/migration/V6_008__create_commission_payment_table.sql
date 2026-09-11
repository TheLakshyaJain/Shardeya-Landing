-- B-14 §20.5 commission_payment (01-DATA-MODEL.md §5) -- "immutable" per
-- the data model, same insert-only/no-DELETE-RULE shape as payment_record
-- (V3_003): corrections are reversals (negative amount rows via
-- reverses_payment_id), never edits or deletes, matching CLAUDE.md rule
-- #2/#10 applied to commissions, not just buyer payments. Deliberately NO
-- deleted_at, same reasoning V3_003's own comment gives -- a RULE blocking
-- DELETE outright makes a soft-delete column meaningless dead schema here.
--
-- mode reuses payment_mode (V3_003) rather than a duplicate identical
-- enum -- commission payouts use the exact same channels a buyer payment
-- does (cash/cheque/bank transfer/UPI/DD), and Postgres enums are global
-- types, not scoped to one table.
CREATE TABLE commission_payment (
    id                          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    org_id                      UUID NOT NULL REFERENCES organization(id),
    commission_ledger_entry_id  UUID NOT NULL REFERENCES commission_ledger_entry(id),
    amount                      NUMERIC(19,2) NOT NULL CHECK (amount <> 0),
    paid_on                     DATE NOT NULL,
    mode                        payment_mode NOT NULL,
    reference                   VARCHAR(120),
    paid_by                     UUID REFERENCES app_user(id),
    remarks                     TEXT,
    reverses_payment_id         UUID REFERENCES commission_payment(id),
    created_at                  TIMESTAMPTZ NOT NULL DEFAULT now(),
    created_by                  UUID REFERENCES app_user(id),
    updated_at                  TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_by                  UUID REFERENCES app_user(id),
    version                     BIGINT NOT NULL DEFAULT 0
    -- Deliberately NO deleted_at -- see the RULE below instead.
);

CREATE INDEX ix_commission_payment_org ON commission_payment(org_id);
CREATE INDEX ix_commission_payment_ledger ON commission_payment(commission_ledger_entry_id);

CREATE RULE no_delete_commission_payment AS ON DELETE TO commission_payment DO INSTEAD NOTHING;

ALTER TABLE commission_payment ENABLE ROW LEVEL SECURITY;
ALTER TABLE commission_payment FORCE ROW LEVEL SECURITY;
CREATE POLICY tenant_isolation ON commission_payment
    USING (org_id = NULLIF(current_setting('app.current_org', true), '')::uuid);
