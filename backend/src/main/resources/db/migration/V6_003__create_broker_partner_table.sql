-- B-14 §20.1/§20.2 broker_partner (01-DATA-MODEL.md §5). bank_account_number_enc
-- reuses GovIdCipher (platform.GovIdCipher) -- despite its gov-ID-specific
-- name/docs, it's a genuinely generic AES-256-GCM byte[]-in/byte[]-out
-- utility; a second field encrypted with the same key is cryptographically
-- fine (GCM's per-encryption random nonce keeps ciphertexts independent),
-- and renaming the class for one new caller would be unrelated churn this
-- milestone doesn't need.
CREATE TYPE broker_commission_type AS ENUM ('PERCENTAGE', 'FIXED');
CREATE TYPE broker_status AS ENUM ('ACTIVE', 'INACTIVE', 'BLOCKED');

CREATE TABLE broker_partner (
    id                          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    org_id                      UUID NOT NULL REFERENCES organization(id),
    full_name                   VARCHAR(120) NOT NULL,
    mobile                      VARCHAR(15) NOT NULL,
    email                       VARCHAR(255),
    city_area                   VARCHAR(150),
    rera_number                 VARCHAR(60),
    firm_name                   VARCHAR(150),
    commission_type             broker_commission_type NOT NULL,
    commission_pct              NUMERIC(6,3),
    commission_fixed            NUMERIC(19,2),
    per_project_rates_enabled   BOOLEAN NOT NULL DEFAULT false,
    bank_account_name           VARCHAR(150),
    bank_account_number_enc     BYTEA,
    bank_account_last4          VARCHAR(4),
    ifsc                        VARCHAR(11),
    upi_id                      VARCHAR(80),
    notes                       TEXT,
    tier_id                     UUID,
    tier_manually_overridden    BOOLEAN NOT NULL DEFAULT false,
    tier_assigned_at            TIMESTAMPTZ,
    deals_closed_count          INTEGER NOT NULL DEFAULT 0,
    total_commission_earned     NUMERIC(19,2) NOT NULL DEFAULT 0,
    total_commission_paid       NUMERIC(19,2) NOT NULL DEFAULT 0,
    last_active_at              TIMESTAMPTZ,
    status                      broker_status NOT NULL DEFAULT 'ACTIVE',
    linked_user_id              UUID REFERENCES app_user(id),
    created_at                  TIMESTAMPTZ NOT NULL DEFAULT now(),
    created_by                  UUID REFERENCES app_user(id),
    updated_at                  TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_by                  UUID REFERENCES app_user(id),
    deleted_at                  TIMESTAMPTZ,
    deleted_by                  UUID REFERENCES app_user(id),
    version                     BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT fk_broker_partner_tier FOREIGN KEY (tier_id) REFERENCES broker_tier(id),
    CONSTRAINT ck_broker_partner_commission_pct CHECK (commission_type <> 'PERCENTAGE' OR (commission_pct IS NOT NULL AND commission_pct >= 0 AND commission_pct <= 20)),
    CONSTRAINT ck_broker_partner_commission_fixed CHECK (commission_type <> 'FIXED' OR (commission_fixed IS NOT NULL AND commission_fixed > 0 AND commission_fixed <= 10000000)),
    CONSTRAINT ck_broker_partner_ifsc CHECK (ifsc IS NULL OR ifsc ~ '^[A-Z]{4}0[A-Z0-9]{6}$'),
    CONSTRAINT ck_broker_partner_upi CHECK (upi_id IS NULL OR upi_id ~ '^[\w.\-]{2,}@[a-zA-Z]{2,}$')
);

CREATE INDEX ix_broker_partner_org ON broker_partner(org_id) WHERE deleted_at IS NULL;
CREATE INDEX ix_broker_partner_tier ON broker_partner(tier_id) WHERE deleted_at IS NULL;

-- Duplicate broker mobile within the org is blocked with a link to the
-- existing record (B-14 §10) -- enforced here, the service layer just
-- turns the constraint violation into that friendly response.
CREATE UNIQUE INDEX ux_broker_partner_mobile ON broker_partner(org_id, mobile) WHERE deleted_at IS NULL;

ALTER TABLE broker_partner ENABLE ROW LEVEL SECURITY;
ALTER TABLE broker_partner FORCE ROW LEVEL SECURITY;
CREATE POLICY tenant_isolation ON broker_partner
    USING (org_id = NULLIF(current_setting('app.current_org', true), '')::uuid);
