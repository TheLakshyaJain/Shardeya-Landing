-- 06-BROKER-NETWORK-ENGINE.md §7/§11, build-order step 5 -- one row per
-- beneficiary per DESIGNATION-broker booking (the selling broker itself,
-- plus every upline that received a differential or same-slab bonus,
-- INCLUDING an explicit zero-amount UPLINE_DIFFERENTIAL row when an
-- upline's rate is lower than its direct downline's -- see
-- CommissionCalculationEngine's own javadoc for why that row still exists:
-- §9's future cancellation/recovery logic needs "a reversal record for
-- every beneficiary in the tree, even uplines who did nothing wrong").
--
-- Frozen at booking (this milestone's PlotSaleService.create()) and never
-- touched again except by the trigger below recomputing released_amount --
-- total_amount, the snapshot, and every rate are set once and read-only
-- from then on, matching commission_ledger_entry.config_snapshot's own
-- "self-contained, immune to a later rate/config change" contract (§23).
--
-- rate_snapshot (JSONB) carries the beneficiary's and (for every row past
-- level 0) the direct downline's designation name/name_hi/rate at the
-- instant of freezing -- a JSONB blob, not six separate snapshot columns,
-- mirroring commission_ledger_entry.config_snapshot's own established
-- shape rather than inventing a new one for this table.
--
-- §11 lists both "commission_amount" and "total_amount" as separate
-- columns for what is the same figure (plot_area_sqft × commission_per_sqft
-- IS the frozen total for this line) -- consolidated into one
-- total_amount column here rather than a pointless duplicate that could
-- drift out of sync.
--
-- paid_amount exists in the schema (per §11's own column list) but has NO
-- write path in this round -- no endpoint exists yet to record an actual
-- cash/bank payout to a DESIGNATION-broker beneficiary (only released_amount,
-- the automatic proportional-release mechanism, is wired this round). It
-- stays 0 for every row until a future round adds that action, the same
-- "schema built for the full shape, only part of it wired up yet" pattern
-- CLAUDE.md already documents for BUILDER_TEAM_MEMBERS/org_usage.
CREATE TYPE booking_commission_line_type AS ENUM ('SELLING_BROKER', 'UPLINE_DIFFERENTIAL', 'NETWORK_SAME_SLAB_BONUS');
CREATE TYPE booking_commission_status AS ENUM ('PENDING', 'PARTIALLY_RELEASED', 'FULLY_RELEASED', 'CANCELLED');

CREATE TABLE booking_commission (
    id                          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    org_id                      UUID NOT NULL REFERENCES organization(id),
    plot_sale_id                UUID NOT NULL REFERENCES plot_sale(id),
    project_id                  UUID NOT NULL REFERENCES project(id),
    plot_id                     UUID NOT NULL REFERENCES plot(id),
    selling_broker_id           UUID NOT NULL REFERENCES broker_partner(id),
    beneficiary_broker_id       UUID NOT NULL REFERENCES broker_partner(id),
    upline_level                SMALLINT NOT NULL,
    commission_type             booking_commission_line_type NOT NULL,
    rate_snapshot                JSONB NOT NULL,
    plot_area_sqft              NUMERIC(14,4) NOT NULL,
    commission_per_sqft         NUMERIC(19,2) NOT NULL,
    total_amount                NUMERIC(19,2) NOT NULL,
    -- Maintained by a trigger from commission_release (V65_008), same
    -- "trigger-maintained running total, never written directly by
    -- application code" shape as commission_ledger_entry.amount_paid.
    released_amount             NUMERIC(19,2) NOT NULL DEFAULT 0,
    paid_amount                 NUMERIC(19,2) NOT NULL DEFAULT 0,
    pending_amount               NUMERIC(19,2) GENERATED ALWAYS AS (released_amount - paid_amount) STORED,
    status                       booking_commission_status NOT NULL DEFAULT 'PENDING',
    created_at                   TIMESTAMPTZ NOT NULL DEFAULT now(),
    created_by                   UUID REFERENCES app_user(id),
    updated_at                   TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_by                   UUID REFERENCES app_user(id),
    version                      BIGINT NOT NULL DEFAULT 0
    -- Deliberately NO deleted_at -- same reasoning as commission_payment
    -- (V6_008): a booking's commission tree is never deleted, only ever
    -- cancelled (status flip, §9, not built this round).
);

CREATE INDEX ix_booking_commission_org ON booking_commission(org_id);
CREATE INDEX ix_booking_commission_sale ON booking_commission(plot_sale_id);
CREATE INDEX ix_booking_commission_beneficiary ON booking_commission(beneficiary_broker_id);

-- One frozen tree per booking; re-selling a cancelled plot creates a new
-- plot_sale_id (same precedent as commission_ledger_entry's own
-- ux_commission_ledger_entry_sale), so this doesn't block re-selling.
CREATE UNIQUE INDEX ux_booking_commission_sale_beneficiary_type
    ON booking_commission(plot_sale_id, beneficiary_broker_id, commission_type);

ALTER TABLE booking_commission ENABLE ROW LEVEL SECURITY;
ALTER TABLE booking_commission FORCE ROW LEVEL SECURITY;
CREATE POLICY tenant_isolation ON booking_commission
    USING (org_id = NULLIF(current_setting('app.current_org', true), '')::uuid);
