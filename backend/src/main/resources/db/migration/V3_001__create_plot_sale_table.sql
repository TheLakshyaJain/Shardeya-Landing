-- B-04 Plot Sale, Buyer & Documents (01-DATA-MODEL.md §4 plot_sale / §12.3.2).
-- customer_id and broker_partner_id have NO FK yet: customer (M-12) and
-- broker_partner (B-14) don't exist until later milestones -- same pattern
-- as plot.current_sale_id before this migration (see V2_005's own comment).
-- Add the FKs when those tables land. Until B-14 exists, broker attribution
-- is external-name/mobile + a manually entered commission snapshot only --
-- B-04 §7 already describes this as the fallback path for a broker "not in
-- the system"; M3 uses that path for every sale, not just the fallback case.
CREATE TYPE plot_sale_payment_type AS ENUM ('LUMP_SUM', 'INSTALMENT');
CREATE TYPE plot_sale_status AS ENUM ('BOOKED', 'ACTIVE', 'COMPLETED', 'CANCELLED');
CREATE TYPE gov_id_type AS ENUM ('AADHAAR', 'PAN', 'PASSPORT', 'VOTER_ID', 'DL');

CREATE TABLE plot_sale (
    id                      UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    org_id                  UUID NOT NULL REFERENCES organization(id),
    plot_id                 UUID NOT NULL REFERENCES plot(id),
    project_id              UUID NOT NULL REFERENCES project(id),
    customer_id             UUID,
    buyer_name              VARCHAR(120) NOT NULL,
    buyer_mobile            VARCHAR(15) NOT NULL,
    buyer_email             VARCHAR(255),
    buyer_gov_id_type       gov_id_type,
    -- AES-256-GCM ciphertext (12-byte nonce || ciphertext || 16-byte tag),
    -- encrypted/decrypted at the application layer (GovIdCipher), never in
    -- SQL -- see that class for the key-management comment.
    buyer_gov_id_number_enc BYTEA,
    buyer_gov_id_last4      VARCHAR(4),
    buyer_gov_id_media_id   UUID,
    purchase_date           DATE NOT NULL,
    deal_value              NUMERIC(19,2) NOT NULL,
    broker_partner_id       UUID,
    external_broker_name    VARCHAR(120),
    external_broker_mobile  VARCHAR(15),
    broker_commission_amount NUMERIC(19,2),
    payment_type            plot_sale_payment_type NOT NULL,
    status                  plot_sale_status NOT NULL DEFAULT 'ACTIVE',
    cancelled_at             TIMESTAMPTZ,
    cancellation_reason      TEXT,
    handled_by               UUID REFERENCES app_user(id),
    -- Maintained by a trigger from payment_record (V3_009) -- never written
    -- directly by application code.
    total_paid               NUMERIC(19,2) NOT NULL DEFAULT 0,
    balance_due               NUMERIC(19,2) GENERATED ALWAYS AS (deal_value - total_paid) STORED,
    created_at                TIMESTAMPTZ NOT NULL DEFAULT now(),
    created_by                UUID REFERENCES app_user(id),
    updated_at                TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_by                UUID REFERENCES app_user(id),
    deleted_at                TIMESTAMPTZ,
    deleted_by                UUID REFERENCES app_user(id),
    version                    BIGINT NOT NULL DEFAULT 0
);

CREATE INDEX ix_plot_sale_org ON plot_sale(org_id) WHERE deleted_at IS NULL;
CREATE INDEX ix_plot_sale_project ON plot_sale(project_id) WHERE deleted_at IS NULL;

-- Only one non-cancelled sale per plot at a time -- this is also the
-- concurrency guard: two staff selling the same plot at once makes the
-- second INSERT fail cleanly on this constraint instead of silently
-- double-selling (B-04 §10).
CREATE UNIQUE INDEX ux_plot_active_sale ON plot_sale(plot_id) WHERE status <> 'CANCELLED' AND deleted_at IS NULL;

ALTER TABLE plot_sale ENABLE ROW LEVEL SECURITY;
ALTER TABLE plot_sale FORCE ROW LEVEL SECURITY;
CREATE POLICY tenant_isolation ON plot_sale
    USING (org_id = NULLIF(current_setting('app.current_org', true), '')::uuid);
