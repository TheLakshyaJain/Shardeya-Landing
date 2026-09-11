-- 06-BROKER-NETWORK-ENGINE.md §8a, broker payout tracking -- the third
-- money state (Paid), added after the engine was first built. Earned
-- (booking_commission.total_amount) and Released
-- (booking_commission.released_amount) already existed; this is the
-- builder actually handing the broker money, which never had any
-- representation at all until now.
--
-- Mirrors commission_payment (V6_008, the M6 PERCENTAGE/FIXED-broker
-- payout table) for the payment row itself -- same immutable/reversal
-- shape, same reuse of the global payment_mode enum (a broker payout uses
-- the exact same channels a buyer payment or a PERCENTAGE-broker payout
-- does: cash/cheque/bank transfer/UPI/DD). Deliberately NOT 1:1 with a
-- single booking_commission row the way commission_payment is 1:1 with a
-- single commission_ledger_entry -- a DESIGNATION broker's payout spreads
-- oldest-first across potentially many booking_commission rows (they're a
-- beneficiary of many separate bookings), so there is no single foreign
-- key here to a commission entity at all; broker_commission_payment_allocation
-- (next migration) is what links a payout to the specific entries it
-- settles, mirroring payment_record/payment_allocation's (V3_003/V3_004)
-- one-payment-many-schedules shape instead.
--
-- Immutable, insert-only: corrections are reversals (negative amount rows
-- via reverses_payment_id), never edits/deletes -- same discipline as
-- every other money table in this codebase. Deliberately NO deleted_at,
-- same reasoning commission_payment/commission_release already give: the
-- RULE blocking DELETE outright below makes a soft-delete column
-- meaningless dead schema.
CREATE TABLE broker_commission_payment (
    id                          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    org_id                      UUID NOT NULL REFERENCES organization(id),
    beneficiary_broker_id       UUID NOT NULL REFERENCES broker_partner(id),
    amount                      NUMERIC(19,2) NOT NULL CHECK (amount <> 0),
    paid_on                     DATE NOT NULL,
    mode                        payment_mode NOT NULL,
    reference                   VARCHAR(120),
    remarks                     TEXT,
    reverses_payment_id         UUID REFERENCES broker_commission_payment(id),
    created_at                  TIMESTAMPTZ NOT NULL DEFAULT now(),
    created_by                  UUID REFERENCES app_user(id),
    updated_at                  TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_by                  UUID REFERENCES app_user(id),
    version                     BIGINT NOT NULL DEFAULT 0
);

CREATE INDEX ix_broker_commission_payment_org ON broker_commission_payment(org_id);
CREATE INDEX ix_broker_commission_payment_broker ON broker_commission_payment(beneficiary_broker_id);

CREATE RULE no_delete_broker_commission_payment AS ON DELETE TO broker_commission_payment DO INSTEAD NOTHING;

ALTER TABLE broker_commission_payment ENABLE ROW LEVEL SECURITY;
ALTER TABLE broker_commission_payment FORCE ROW LEVEL SECURITY;
CREATE POLICY tenant_isolation ON broker_commission_payment
    USING (org_id = NULLIF(current_setting('app.current_org', true), '')::uuid);
