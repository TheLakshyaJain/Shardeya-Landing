-- M-09 (01-DATA-MODEL.md §10 plan_limit). limit_value is text because most
-- keys are counts (-1 = unlimited) but a few are string tiers
-- (LEGAL_DOCS: NONE|BASIC|FULL, ANALYTICS: BASIC|ADVANCED|FULL,
-- BACKUP_FREQUENCY: NONE|WEEKLY|DAILY) — parsed as int or compared as
-- string depending on limit_key, same pattern as measurement_unit's
-- reference-data style.
CREATE TABLE plan_limit (
    plan_code    VARCHAR(20) NOT NULL REFERENCES plan(code),
    limit_key    VARCHAR(40) NOT NULL,
    limit_value  VARCHAR(20) NOT NULL,
    PRIMARY KEY (plan_code, limit_key)
);
