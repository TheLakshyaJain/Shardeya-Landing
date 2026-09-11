-- Reference Data (01-DATA-MODEL.md §2 measurement_unit) — platform-managed,
-- no org_id, no RLS (same class as `plan`/`role` system rows).
-- Doc says "PK (code, coalesce(state_code,'--'))", but a PRIMARY KEY can't be
-- an expression in Postgres — a real `id` surrogate key is the actual PK, and
-- a UNIQUE INDEX on the coalesce expression enforces the intended semantic
-- uniqueness (one universal row per unit code, plus state-specific overrides
-- like BIGHA).
CREATE TABLE measurement_unit (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    code            VARCHAR(16) NOT NULL,
    name_en         VARCHAR(60) NOT NULL,
    name_hi         VARCHAR(60) NOT NULL,
    to_sqft_factor  NUMERIC(14,6) NOT NULL,
    state_code      VARCHAR(2), -- VARCHAR not CHAR — see V2_003's comment (same bug class as V1_008)
    is_active       BOOLEAN NOT NULL DEFAULT true
);

CREATE UNIQUE INDEX ux_measurement_unit_code_state
    ON measurement_unit(code, coalesce(state_code, '--'));
