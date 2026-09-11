-- B-14 §20.5 commission_ledger_entry (01-DATA-MODEL.md §5). One row per
-- sale actually attributed to a real broker_partner_id -- an
-- external-broker (free-text) sale never gets a row at all (B-14 §10: "no
-- ledger automation"), so this table's population is inherently sparser
-- than plot_sale itself, not one-row-per-sale universally.
--
-- config_snapshot (JSONB) is the audit trail proving exactly which
-- commission_config row (or the broker's own default) produced
-- base_commission, plus the tier and its bonus at the moment of the sale --
-- CLAUDE.md's own money rules plus B-14 §7's explicit "rate changes are
-- never retroactive" both demand this snapshot be self-contained: even if
-- the source commission_config row is later edited or deleted, this JSONB
-- blob alone must be enough to explain the historical number.
CREATE TYPE commission_ledger_status AS ENUM ('PENDING', 'PARTIALLY_PAID', 'PAID', 'CANCELLED');

CREATE TABLE commission_ledger_entry (
    id                  UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    org_id              UUID NOT NULL REFERENCES organization(id),
    broker_partner_id   UUID NOT NULL REFERENCES broker_partner(id),
    plot_sale_id        UUID NOT NULL REFERENCES plot_sale(id),
    project_id          UUID NOT NULL REFERENCES project(id),
    plot_id             UUID NOT NULL REFERENCES plot(id),
    deal_date           DATE NOT NULL,
    deal_value          NUMERIC(19,2) NOT NULL,
    base_commission     NUMERIC(19,2) NOT NULL,
    tier_bonus          NUMERIC(19,2) NOT NULL DEFAULT 0,
    total_commission    NUMERIC(19,2) GENERATED ALWAYS AS (base_commission + tier_bonus) STORED,
    -- Maintained by a trigger from commission_payment (V6_014), same
    -- "trigger-maintained running total, never written directly by
    -- application code" shape as plot_sale.total_paid (V3_010).
    amount_paid         NUMERIC(19,2) NOT NULL DEFAULT 0,
    balance_due         NUMERIC(19,2) GENERATED ALWAYS AS (base_commission + tier_bonus - amount_paid) STORED,
    status               commission_ledger_status NOT NULL DEFAULT 'PENDING',
    config_snapshot       JSONB NOT NULL,
    created_at            TIMESTAMPTZ NOT NULL DEFAULT now(),
    created_by            UUID REFERENCES app_user(id),
    updated_at            TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_by            UUID REFERENCES app_user(id),
    deleted_at            TIMESTAMPTZ,
    deleted_by            UUID REFERENCES app_user(id),
    version               BIGINT NOT NULL DEFAULT 0
);

CREATE INDEX ix_commission_ledger_entry_org ON commission_ledger_entry(org_id) WHERE deleted_at IS NULL;
CREATE INDEX ix_commission_ledger_entry_broker ON commission_ledger_entry(broker_partner_id) WHERE deleted_at IS NULL;
CREATE INDEX ix_commission_ledger_entry_project ON commission_ledger_entry(project_id) WHERE deleted_at IS NULL;

-- One ledger entry per sale (B-14 §20.5: "One commission_ledger_entry per
-- attributed sale, created at sale time") -- a second sale on the same
-- plot after a cancellation gets its own new plot_sale_id and therefore
-- its own new ledger entry, so this doesn't block re-selling a plot.
CREATE UNIQUE INDEX ux_commission_ledger_entry_sale ON commission_ledger_entry(plot_sale_id) WHERE deleted_at IS NULL;

ALTER TABLE commission_ledger_entry ENABLE ROW LEVEL SECURITY;
ALTER TABLE commission_ledger_entry FORCE ROW LEVEL SECURITY;
CREATE POLICY tenant_isolation ON commission_ledger_entry
    USING (org_id = NULLIF(current_setting('app.current_org', true), '')::uuid);
