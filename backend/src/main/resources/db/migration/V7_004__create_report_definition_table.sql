-- M-10 §3/§7 "a report definition is data": one row + one query per report,
-- not a bespoke endpoint per report -- the whole point of the shared engine
-- (M-10 §1: sixteen bespoke endpoints would be sixteen places to introduce
-- a tenant leak). No org_id / RLS -- this is reference data shared by every
-- tenant, same shape as measurement_unit / stamp_duty_rate, not a
-- tenant-owned table.
CREATE TABLE report_definition (
    code                 VARCHAR(40) PRIMARY KEY,
    profile              VARCHAR(10) NOT NULL,
    name_en              VARCHAR(120) NOT NULL,
    name_hi              VARCHAR(120) NOT NULL,
    description_key      VARCHAR(120) NOT NULL,
    supported_filters    JSONB NOT NULL DEFAULT '[]',
    supported_formats    JSONB NOT NULL DEFAULT '["XLSX","CSV"]',
    required_permission  VARCHAR(60) NOT NULL,
    min_analytics_tier   VARCHAR(20),
    sort_order           INTEGER NOT NULL DEFAULT 0,
    CONSTRAINT ck_report_definition_profile CHECK (profile IN ('BROKER', 'BUILDER'))
);
