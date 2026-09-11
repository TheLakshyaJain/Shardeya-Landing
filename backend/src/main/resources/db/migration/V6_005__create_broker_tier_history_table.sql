-- B-14 §3 broker_tier_history -- an append-only audit trail of every tier
-- change (auto-evaluated or manual override), never updated once written.
CREATE TABLE broker_tier_history (
    id                  UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    org_id              UUID NOT NULL REFERENCES organization(id),
    broker_partner_id   UUID NOT NULL REFERENCES broker_partner(id),
    from_tier_id        UUID REFERENCES broker_tier(id),
    to_tier_id          UUID NOT NULL REFERENCES broker_tier(id),
    deals_at_change     INTEGER NOT NULL,
    changed_at          TIMESTAMPTZ NOT NULL DEFAULT now(),
    changed_by          UUID REFERENCES app_user(id),
    is_manual           BOOLEAN NOT NULL DEFAULT false,
    reason              TEXT
);

CREATE INDEX ix_broker_tier_history_org ON broker_tier_history(org_id);
CREATE INDEX ix_broker_tier_history_broker ON broker_tier_history(broker_partner_id);

ALTER TABLE broker_tier_history ENABLE ROW LEVEL SECURITY;
ALTER TABLE broker_tier_history FORCE ROW LEVEL SECURITY;
CREATE POLICY tenant_isolation ON broker_tier_history
    USING (org_id = NULLIF(current_setting('app.current_org', true), '')::uuid);
