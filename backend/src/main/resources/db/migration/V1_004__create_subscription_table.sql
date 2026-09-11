CREATE TYPE subscription_status AS ENUM ('TRIAL', 'ACTIVE', 'PAST_DUE', 'CANCELLED', 'EXPIRED');

CREATE TABLE subscription (
    id                    UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    org_id                UUID NOT NULL REFERENCES organization(id),
    plan_code             VARCHAR(20) NOT NULL REFERENCES plan(code),
    status                subscription_status NOT NULL,
    started_at            TIMESTAMPTZ NOT NULL DEFAULT now(),
    current_period_start  TIMESTAMPTZ NOT NULL DEFAULT now(),
    current_period_end    TIMESTAMPTZ NOT NULL,
    auto_renew            BOOLEAN NOT NULL DEFAULT false,
    cancelled_at          TIMESTAMPTZ,
    cancel_at_period_end  BOOLEAN NOT NULL DEFAULT false,
    grace_until           TIMESTAMPTZ,
    created_at            TIMESTAMPTZ NOT NULL DEFAULT now(),
    created_by            UUID REFERENCES app_user(id),
    updated_at            TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_by            UUID REFERENCES app_user(id),
    deleted_at            TIMESTAMPTZ,
    deleted_by            UUID REFERENCES app_user(id),
    version               BIGINT NOT NULL DEFAULT 0
);

CREATE INDEX ix_subscription_org ON subscription(org_id) WHERE deleted_at IS NULL;

ALTER TABLE subscription ENABLE ROW LEVEL SECURITY;
ALTER TABLE subscription FORCE ROW LEVEL SECURITY;

CREATE POLICY tenant_isolation ON subscription
    USING (org_id = current_setting('app.current_org')::uuid);
