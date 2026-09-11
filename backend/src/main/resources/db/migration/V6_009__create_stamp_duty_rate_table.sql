-- M-08 §3.4.3 stamp_duty_rate (01-DATA-MODEL.md §142) -- a platform
-- reference table, same shape as measurement_unit/notification_type: no
-- org_id, no RLS, shared read-only-to-tenants catalogue, admin-editable
-- (M-14 basic per 05-MILESTONES.md M6 exit criteria: "Stamp duty rates
-- editable (admin endpoint, M14 basic)").
CREATE TYPE stamp_duty_property_type AS ENUM ('RESIDENTIAL', 'COMMERCIAL', 'AGRICULTURAL');
CREATE TYPE stamp_duty_transaction_type AS ENUM ('SALE', 'GIFT', 'MORTGAGE');
CREATE TYPE stamp_duty_buyer_gender AS ENUM ('MALE', 'FEMALE', 'JOINT', 'ANY');

CREATE TABLE stamp_duty_rate (
    id                  UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    state_code          VARCHAR(2) NOT NULL, -- VARCHAR not CHAR -- same bug class as V1_008/V2_003/V2_006/V6_003
    property_type       stamp_duty_property_type NOT NULL,
    transaction_type     stamp_duty_transaction_type NOT NULL,
    buyer_gender          stamp_duty_buyer_gender NOT NULL,
    stamp_duty_pct         NUMERIC(6,3) NOT NULL,
    registration_pct        NUMERIC(6,3),
    registration_flat       NUMERIC(19,2),
    registration_cap        NUMERIC(19,2),
    effective_from           DATE NOT NULL,
    effective_to             DATE,
    source_note              TEXT,
    created_at                TIMESTAMPTZ NOT NULL DEFAULT now(),
    created_by                UUID REFERENCES app_user(id),
    updated_at                TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_by                UUID REFERENCES app_user(id),
    CONSTRAINT ck_stamp_duty_rate_date_range CHECK (effective_to IS NULL OR effective_to >= effective_from)
);

CREATE UNIQUE INDEX ux_stamp_duty_rate ON stamp_duty_rate(state_code, property_type, transaction_type, buyer_gender, effective_from);

-- Lookup path (M-08 §7): "look up by (state, propertyType, transactionType,
-- gender) effective today, falling back to gender=ANY" -- this index makes
-- both the exact-gender and the ANY-fallback lookup a plain index scan.
CREATE INDEX ix_stamp_duty_rate_lookup ON stamp_duty_rate(state_code, property_type, transaction_type, buyer_gender, effective_from);
