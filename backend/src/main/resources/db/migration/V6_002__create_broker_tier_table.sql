-- B-14 §20.4 broker_tier (01-DATA-MODEL.md §5). Unlike measurement_unit or
-- notification_type (shared, org_id-less reference tables), tiers are
-- genuinely per-org: "Default tiers seeded per org... all names,
-- thresholds, bonuses, badges and perks editable per org." Each BUILDER
-- org gets its own independently-editable set of rows, backfilled for
-- existing orgs in V6_011 and seeded at signup time for new ones
-- (AuthService.verifySignupOtp, mirroring how a new org already gets its
-- FREE subscription row in that same transaction).
CREATE TYPE broker_tier_bonus_type AS ENUM ('PCT', 'FIXED', 'NONE');

CREATE TABLE broker_tier (
    id                  UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    org_id              UUID NOT NULL REFERENCES organization(id),
    name                VARCHAR(40) NOT NULL,
    name_hi             VARCHAR(40),
    min_deals           INTEGER NOT NULL,
    max_deals           INTEGER,
    bonus_type          broker_tier_bonus_type NOT NULL DEFAULT 'NONE',
    bonus_value         NUMERIC(19,3) NOT NULL DEFAULT 0,
    badge_media_id      UUID,
    perks_description   TEXT,
    sort_order          SMALLINT NOT NULL DEFAULT 0,
    is_active           BOOLEAN NOT NULL DEFAULT true,
    created_at          TIMESTAMPTZ NOT NULL DEFAULT now(),
    created_by          UUID REFERENCES app_user(id),
    updated_at          TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_by          UUID REFERENCES app_user(id),
    deleted_at          TIMESTAMPTZ,
    deleted_by          UUID REFERENCES app_user(id),
    version             BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT ck_broker_tier_range CHECK (max_deals IS NULL OR max_deals >= min_deals),
    CONSTRAINT ck_broker_tier_min_deals_nonneg CHECK (min_deals >= 0),
    CONSTRAINT ck_broker_tier_bonus_pct_range CHECK (bonus_type <> 'PCT' OR (bonus_value >= 0 AND bonus_value <= 100)),
    CONSTRAINT ck_broker_tier_bonus_value_nonneg CHECK (bonus_value >= 0)
);

CREATE INDEX ix_broker_tier_org ON broker_tier(org_id) WHERE deleted_at IS NULL;

CREATE UNIQUE INDEX ux_broker_tier_name ON broker_tier(org_id, lower(name)) WHERE deleted_at IS NULL;

-- Overlapping [min_deals, max_deals] ranges within the same org are
-- rejected outright (B-14 §20.4). int4range's own constructor treats a
-- NULL upper bound as genuinely unbounded ("25+" style tiers), not as SQL
-- NULL-propagation into a meaningless range -- so max_deals + 1 (int4range
-- upper bounds are exclusive; max_deals itself is inclusive) naturally
-- stays NULL for an open-ended tier and int4range(min_deals, NULL) becomes
-- "[min_deals, infinity)", correctly overlap-checked via && against every
-- other row, bounded or not, with no special-casing needed.
ALTER TABLE broker_tier ADD CONSTRAINT ex_broker_tier_no_overlap
    EXCLUDE USING gist (
        org_id WITH =,
        int4range(min_deals, max_deals + 1) WITH &&
    ) WHERE (deleted_at IS NULL);

ALTER TABLE broker_tier ENABLE ROW LEVEL SECURITY;
ALTER TABLE broker_tier FORCE ROW LEVEL SECURITY;
CREATE POLICY tenant_isolation ON broker_tier
    USING (org_id = NULLIF(current_setting('app.current_org', true), '')::uuid);
