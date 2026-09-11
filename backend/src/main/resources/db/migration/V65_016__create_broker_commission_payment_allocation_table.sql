-- 06-BROKER-NETWORK-ENGINE.md §8a -- links one broker_commission_payment
-- to the specific booking_commission entries it settles, exactly the role
-- payment_allocation (V3_004) plays between payment_record and
-- payment_schedule. One payout can span several entries (oldest-first
-- auto-allocation, BrokerCommissionPaymentService's own job); one entry
-- can be settled across several payouts over time.
--
-- Shape mirrors commission_release (V65_007), NOT payment_allocation --
-- clean immutable insert-only rows, no deleted_at, a RULE blocking DELETE
-- outright, since nothing in this codebase's broker-payout path ever
-- edits or removes an allocation row after insert (a correction is a
-- reversal payment with its own mirrored, negated allocation rows, never
-- a mutation of the original's).
CREATE TABLE broker_commission_payment_allocation (
    id                              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    org_id                          UUID NOT NULL REFERENCES organization(id),
    broker_commission_payment_id    UUID NOT NULL REFERENCES broker_commission_payment(id),
    booking_commission_id           UUID NOT NULL REFERENCES booking_commission(id),
    amount                          NUMERIC(19,2) NOT NULL CHECK (amount <> 0),
    created_at                      TIMESTAMPTZ NOT NULL DEFAULT now(),
    created_by                      UUID REFERENCES app_user(id),
    version                         BIGINT NOT NULL DEFAULT 0
);

CREATE INDEX ix_broker_commission_payment_allocation_org ON broker_commission_payment_allocation(org_id);
CREATE INDEX ix_broker_commission_payment_allocation_payment ON broker_commission_payment_allocation(broker_commission_payment_id);
CREATE INDEX ix_broker_commission_payment_allocation_booking_commission ON broker_commission_payment_allocation(booking_commission_id);

CREATE RULE no_delete_broker_commission_payment_allocation AS ON DELETE TO broker_commission_payment_allocation DO INSTEAD NOTHING;

ALTER TABLE broker_commission_payment_allocation ENABLE ROW LEVEL SECURITY;
ALTER TABLE broker_commission_payment_allocation FORCE ROW LEVEL SECURITY;
CREATE POLICY tenant_isolation ON broker_commission_payment_allocation
    USING (org_id = NULLIF(current_setting('app.current_org', true), '')::uuid);
