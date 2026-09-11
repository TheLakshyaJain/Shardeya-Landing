-- M-12 Customer / Lead Core (01-DATA-MODEL.md §3). One entity shared by
-- broker (§6) and builder (§13) profiles -- the builder-only columns
-- (interested_project_id, interested_plot_id, assigned_to, source_broker_id)
-- are simply NULL for broker orgs. source_broker_id has NO FK yet -- B-14
-- (broker_partner) doesn't exist until a later milestone, same deferred-FK
-- pattern V3_001 already used for plot_sale.broker_partner_id.
CREATE TYPE customer_source AS ENUM (
    'REFERRAL', 'FACEBOOK', 'INSTAGRAM', 'WALK_IN', 'COLD_CALL', 'WEBSITE',
    'BROKER', 'EXHIBITION', 'SOCIAL_MEDIA', 'OTHER'
);
CREATE TYPE lead_status AS ENUM (
    'INTERESTED', 'SITE_VISIT_SCHEDULED', 'SITE_VISIT_DONE', 'FOLLOWING_UP',
    'DEAL_CLOSED', 'LOST'
);
CREATE TYPE preferred_property_type AS ENUM ('PLOT', 'FLAT', 'HOUSE');

CREATE TABLE customer (
    id                       UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    org_id                   UUID NOT NULL REFERENCES organization(id),
    full_name                VARCHAR(120) NOT NULL,
    mobile                   VARCHAR(15) NOT NULL,
    alternate_mobile         VARCHAR(15),
    email                    VARCHAR(255),
    budget_min               NUMERIC(19,2) NOT NULL,
    budget_max               NUMERIC(19,2) NOT NULL,
    preferred_property_type  preferred_property_type,
    preferred_locality       VARCHAR(150),
    size_requirement         VARCHAR(80),
    source                   customer_source NOT NULL,
    source_broker_id         UUID,
    status                   lead_status NOT NULL DEFAULT 'INTERESTED',
    interested_project_id    UUID REFERENCES project(id),
    interested_plot_id       UUID REFERENCES plot(id),
    assigned_to              UUID REFERENCES app_user(id),
    follow_up_date           DATE,
    no_further_follow_up     BOOLEAN NOT NULL DEFAULT false,
    is_important             BOOLEAN NOT NULL DEFAULT false,
    remarks                  TEXT,
    last_interaction_at      TIMESTAMPTZ,
    closed_at                DATE,
    search_vector            TSVECTOR GENERATED ALWAYS AS (
                                 to_tsvector('simple',
                                     coalesce(full_name, '') || ' ' ||
                                     coalesce(mobile, '') || ' ' ||
                                     coalesce(remarks, ''))
                             ) STORED,
    created_at               TIMESTAMPTZ NOT NULL DEFAULT now(),
    created_by               UUID REFERENCES app_user(id),
    updated_at                TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_by                UUID REFERENCES app_user(id),
    deleted_at                TIMESTAMPTZ,
    deleted_by                UUID REFERENCES app_user(id),
    version                    BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT chk_customer_budget CHECK (budget_max >= budget_min)
);

CREATE INDEX ix_cust_org ON customer(org_id) WHERE deleted_at IS NULL;
CREATE INDEX ix_cust_followup ON customer(org_id, follow_up_date)
    WHERE deleted_at IS NULL AND no_further_follow_up = false
      AND status NOT IN ('DEAL_CLOSED', 'LOST');
CREATE INDEX ix_cust_status ON customer(org_id, status) WHERE deleted_at IS NULL;
CREATE INDEX ix_cust_assigned ON customer(org_id, assigned_to) WHERE deleted_at IS NULL;
CREATE INDEX ix_cust_project ON customer(org_id, interested_project_id) WHERE deleted_at IS NULL;
CREATE INDEX ix_cust_important ON customer(org_id) WHERE is_important AND deleted_at IS NULL;
CREATE INDEX ix_cust_search ON customer USING GIN(search_vector);
CREATE INDEX ix_cust_mobile_trgm ON customer USING GIN(mobile gin_trgm_ops);

ALTER TABLE customer ENABLE ROW LEVEL SECURITY;
ALTER TABLE customer FORCE ROW LEVEL SECURITY;
CREATE POLICY tenant_isolation ON customer
    USING (org_id = NULLIF(current_setting('app.current_org', true), '')::uuid);
