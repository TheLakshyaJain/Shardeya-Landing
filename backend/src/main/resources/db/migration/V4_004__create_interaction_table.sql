-- M-12 §3 interaction: the universal append-only follow-up log (§6.5, §13.3).
-- No deleted_at usage -- deletion is blocked outright by a RULE, same
-- immutability pattern payment_record already established in M3 (CLAUDE.md
-- "payment_record rows are genuinely insert-only ... a Postgres RULE blocks
-- DELETE outright"). Amendment (not deletion) is how a mistake gets fixed,
-- gated to a 15-minute grace window at the application layer
-- (InteractionService), with amended_at/amended_by/original_remarks as the
-- audit trail of what the entry looked like before the edit.
CREATE TYPE interaction_type AS ENUM ('CALL', 'VISIT', 'WHATSAPP', 'MEETING', 'EMAIL', 'SMS', 'NOTE');
CREATE TYPE interaction_result AS ENUM ('POSITIVE', 'NEUTRAL', 'NEGATIVE', 'NOT_INTERESTED', 'NEXT_SCHEDULED', 'NO_FURTHER');

CREATE TABLE interaction (
    id                    UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    org_id                UUID NOT NULL REFERENCES organization(id),
    customer_id           UUID NOT NULL REFERENCES customer(id),
    property_id           UUID,
    project_id            UUID REFERENCES project(id),
    plot_id               UUID REFERENCES plot(id),
    deal_id               UUID,
    occurred_on           DATE NOT NULL,
    type                  interaction_type NOT NULL,
    remarks               TEXT NOT NULL,
    next_follow_up_date   DATE,
    result                interaction_result,
    conducted_by          UUID REFERENCES app_user(id),
    amended_at            TIMESTAMPTZ,
    amended_by            UUID REFERENCES app_user(id),
    original_remarks      TEXT,
    created_at            TIMESTAMPTZ NOT NULL DEFAULT now(),
    created_by            UUID REFERENCES app_user(id),
    updated_at            TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_by            UUID REFERENCES app_user(id),
    version               BIGINT NOT NULL DEFAULT 0
);

CREATE INDEX ix_interaction_org ON interaction(org_id);
CREATE INDEX ix_interaction_cust ON interaction(customer_id, occurred_on DESC);

-- Append-only: DELETE is a documented no-op, never an error, matching the
-- same rationale payment_record's own rule already established (a client
-- retrying a delete shouldn't get a confusing failure -- it just silently
-- doesn't happen, which is correct for an audit-integrity record).
CREATE RULE no_delete_interaction AS ON DELETE TO interaction DO INSTEAD NOTHING;

ALTER TABLE interaction ENABLE ROW LEVEL SECURITY;
ALTER TABLE interaction FORCE ROW LEVEL SECURITY;
CREATE POLICY tenant_isolation ON interaction
    USING (org_id = NULLIF(current_setting('app.current_org', true), '')::uuid);
