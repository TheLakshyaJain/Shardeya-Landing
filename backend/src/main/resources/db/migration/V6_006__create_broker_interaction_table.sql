-- B-14 §3/§20.6 broker_interaction -- "follow-up history and notes" on a
-- broker's profile. Same shape/purpose as foundation.customer's own
-- Interaction (M-12), but a distinct table since brokers and leads are
-- unrelated entities with no shared parent to hang a polymorphic
-- interaction table off.
CREATE TABLE broker_interaction (
    id                      UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    org_id                  UUID NOT NULL REFERENCES organization(id),
    broker_partner_id       UUID NOT NULL REFERENCES broker_partner(id),
    occurred_on             DATE NOT NULL,
    type                    VARCHAR(20),
    remarks                 TEXT NOT NULL,
    next_follow_up_date     DATE,
    conducted_by            UUID REFERENCES app_user(id),
    created_at              TIMESTAMPTZ NOT NULL DEFAULT now(),
    created_by              UUID REFERENCES app_user(id),
    updated_at              TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_by              UUID REFERENCES app_user(id),
    deleted_at              TIMESTAMPTZ,
    deleted_by              UUID REFERENCES app_user(id),
    version                 BIGINT NOT NULL DEFAULT 0
);

CREATE INDEX ix_broker_interaction_org ON broker_interaction(org_id) WHERE deleted_at IS NULL;
CREATE INDEX ix_broker_interaction_broker ON broker_interaction(broker_partner_id) WHERE deleted_at IS NULL;

ALTER TABLE broker_interaction ENABLE ROW LEVEL SECURITY;
ALTER TABLE broker_interaction FORCE ROW LEVEL SECURITY;
CREATE POLICY tenant_isolation ON broker_interaction
    USING (org_id = NULLIF(current_setting('app.current_org', true), '')::uuid);
