-- B-14 §20.3 broker_commission_config (01-DATA-MODEL.md §5). No FK to
-- project/plot here (unlike plot_sale, which is itself the reason those
-- tables exist by this point) -- both already exist by M6, so project_id/
-- plot_id DO get real FKs, unlike plot_sale.broker_partner_id's own
-- multi-milestone FK debt (V3_001, closed retroactively in V6_010 once
-- broker_partner exists).
CREATE TYPE broker_commission_scope AS ENUM ('GLOBAL', 'PROJECT', 'PLOT');

CREATE TABLE broker_commission_config (
    id                  UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    org_id              UUID NOT NULL REFERENCES organization(id),
    broker_partner_id   UUID NOT NULL REFERENCES broker_partner(id),
    scope               broker_commission_scope NOT NULL,
    project_id          UUID REFERENCES project(id),
    plot_id             UUID REFERENCES plot(id),
    commission_type     broker_commission_type NOT NULL,
    rate_value          NUMERIC(19,3) NOT NULL,
    effective_from      DATE NOT NULL,
    effective_to        DATE,
    created_at          TIMESTAMPTZ NOT NULL DEFAULT now(),
    created_by          UUID REFERENCES app_user(id),
    updated_at          TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_by          UUID REFERENCES app_user(id),
    deleted_at          TIMESTAMPTZ,
    deleted_by          UUID REFERENCES app_user(id),
    version             BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT ck_broker_commission_config_scope_target CHECK (
        (scope = 'GLOBAL' AND project_id IS NULL AND plot_id IS NULL) OR
        (scope = 'PROJECT' AND project_id IS NOT NULL AND plot_id IS NULL) OR
        (scope = 'PLOT' AND plot_id IS NOT NULL)
    ),
    CONSTRAINT ck_broker_commission_config_rate_pct CHECK (commission_type <> 'PERCENTAGE' OR (rate_value >= 0 AND rate_value <= 20)),
    CONSTRAINT ck_broker_commission_config_rate_fixed CHECK (commission_type <> 'FIXED' OR (rate_value > 0 AND rate_value <= 10000000)),
    CONSTRAINT ck_broker_commission_config_date_range CHECK (effective_to IS NULL OR effective_to >= effective_from)
);

CREATE INDEX ix_broker_commission_config_org ON broker_commission_config(org_id) WHERE deleted_at IS NULL;
CREATE INDEX ix_broker_commission_config_broker ON broker_commission_config(broker_partner_id) WHERE deleted_at IS NULL;

-- "unique per (broker, scope, target)" (B-14 §11) means at most one config
-- row per exact (broker, scope, project_id, plot_id) combination sharing
-- the same effective_from -- a genuine re-negotiation on the same date
-- would edit the existing row, not create a second one for the same day.
-- NULLs (GLOBAL's project_id/plot_id) compare as distinct in a plain
-- UNIQUE index, which is exactly the desired behaviour: many brokers each
-- get their own GLOBAL row, and coalesce() isn't needed since
-- broker_partner_id already disambiguates them.
CREATE UNIQUE INDEX ux_broker_commission_config_target ON broker_commission_config(
    broker_partner_id, scope, COALESCE(project_id, '00000000-0000-0000-0000-000000000000'),
    COALESCE(plot_id, '00000000-0000-0000-0000-000000000000'), effective_from
) WHERE deleted_at IS NULL;

ALTER TABLE broker_commission_config ENABLE ROW LEVEL SECURITY;
ALTER TABLE broker_commission_config FORCE ROW LEVEL SECURITY;
CREATE POLICY tenant_isolation ON broker_commission_config
    USING (org_id = NULLIF(current_setting('app.current_org', true), '')::uuid);
