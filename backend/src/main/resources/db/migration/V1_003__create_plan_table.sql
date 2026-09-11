CREATE TABLE plan (
    code           VARCHAR(20) PRIMARY KEY,
    name_en        VARCHAR(60) NOT NULL,
    name_hi        VARCHAR(60) NOT NULL,
    price_monthly  NUMERIC(19,2) NOT NULL,
    price_yearly   NUMERIC(19,2) NOT NULL,
    sort_order     SMALLINT NOT NULL,
    is_active      BOOLEAN NOT NULL DEFAULT true
);

-- Platform-managed reference data (like measurement_unit) — no org_id, no RLS.
