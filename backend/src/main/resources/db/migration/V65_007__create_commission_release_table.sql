-- 06-BROKER-NETWORK-ENGINE.md §8/§11, build-order step 6 -- "as the
-- customer pays each instalment, the same fraction of every beneficiary's
-- frozen commission becomes releasable." One row per (booking_commission,
-- triggering customer payment_record) pair -- immutable, insert-only, same
-- "corrections are new rows, never edits/deletes" shape as
-- commission_payment (V6_008) and payment_record/payment_allocation
-- (V3_003/V3_004) before it.
--
-- amount is a DELTA, not the running total: PaymentService's own record()/
-- reverse()/updateChequeStatus() hooks compute, for each affected
-- booking_commission row, the new cumulative "released so far" figure
-- (booking_commission.total_amount x sale.total_paid/sale.deal_value,
-- clamped to [0, total_amount]) and insert exactly one row whose amount is
-- that target minus whatever was already released -- so summing every
-- commission_release row for a given booking_commission_id always equals
-- exactly the correct cumulative figure, with no rounding drift no matter
-- how many small instalments a deal is split across (a naive "release =
-- this payment's own flat fraction of total_amount" per-row approach would
-- drift by a few paise over many rows; this delta-against-the-authoritative-
-- cumulative-total approach can't, the same reason plot_sale.total_paid
-- itself is always a full trigger-recomputed SUM rather than an
-- incrementally-updated running counter).
--
-- This is *why* a payment reversal needs no special-case "reverse this
-- release" logic at all: a reversal is itself a new (negative-amount)
-- payment_record, which drives sale.total_paid down, which drives the
-- freshly-computed target cumulative-released figure down, which produces
-- a negative delta row here -- linked to the reversal payment_record
-- itself, satisfying "link every release to the specific customer payment
-- that triggered it" for the correction the same way it does for the
-- original release.
--
-- payment_record_id (not schedule_id): the release fraction is driven by
-- sale.total_paid/deal_value, i.e. by the raw payment actually recorded,
-- not by which payment_schedule row(s) it happened to allocate against
-- (PaymentAllocationService's own unallocated-advance case already shows
-- allocation and "money the customer actually paid" are not the same
-- question) -- so this links to payment_record, exactly as the task's own
-- instruction says ("the customer payment that triggered it"), not to
-- payment_schedule/payment_allocation.
CREATE TABLE commission_release (
    id                          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    org_id                      UUID NOT NULL REFERENCES organization(id),
    booking_commission_id       UUID NOT NULL REFERENCES booking_commission(id),
    payment_record_id           UUID NOT NULL REFERENCES payment_record(id),
    amount                      NUMERIC(19,2) NOT NULL CHECK (amount <> 0),
    released_on                 DATE NOT NULL,
    created_at                  TIMESTAMPTZ NOT NULL DEFAULT now(),
    created_by                  UUID REFERENCES app_user(id),
    version                     BIGINT NOT NULL DEFAULT 0
    -- Deliberately NO deleted_at/updated_at -- same reasoning as
    -- commission_payment: a RULE blocking DELETE outright (below) makes a
    -- soft-delete column meaningless dead schema, and nothing ever mutates
    -- a release row after insert.
);

CREATE INDEX ix_commission_release_org ON commission_release(org_id);
CREATE INDEX ix_commission_release_booking_commission ON commission_release(booking_commission_id);
CREATE INDEX ix_commission_release_payment_record ON commission_release(payment_record_id);

CREATE RULE no_delete_commission_release AS ON DELETE TO commission_release DO INSTEAD NOTHING;

ALTER TABLE commission_release ENABLE ROW LEVEL SECURITY;
ALTER TABLE commission_release FORCE ROW LEVEL SECURITY;
CREATE POLICY tenant_isolation ON commission_release
    USING (org_id = NULLIF(current_setting('app.current_org', true), '')::uuid);
