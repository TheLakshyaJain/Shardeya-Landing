-- 06-BROKER-NETWORK-ENGINE.md §11 broker_partner extensions.
-- team_successful_bookings is closure-table-rollup maintained -- NOT
-- wired to any trigger yet (that's build-order step 7, COMPLETED
-- promotion logic, explicitly out of scope for this round); it exists
-- here only as a column that defaults to and stays 0 until that step
-- lands. Same for personal_successful_bookings.
ALTER TABLE broker_partner
    ADD COLUMN upline_broker_id                 UUID REFERENCES broker_partner(id),
    ADD COLUMN current_designation_id           UUID REFERENCES designation_slab(id),
    ADD COLUMN current_commission_rate          NUMERIC(19,2),
    ADD COLUMN personal_successful_bookings     INTEGER NOT NULL DEFAULT 0,
    ADD COLUMN team_successful_bookings         INTEGER NOT NULL DEFAULT 0,
    ADD COLUMN designation_manually_overridden  BOOLEAN NOT NULL DEFAULT false;

-- DB-level backstop for the "no self-upline" integrity rule (§10) --
-- cheap, real, and catches it even if application-level validation is
-- ever bypassed. Cycle detection beyond one hop can't be expressed as a
-- CHECK constraint (it needs the closure table / a recursive walk), so
-- that half of §10 stays an application-level check (BrokerNetworkService).
ALTER TABLE broker_partner
    ADD CONSTRAINT ck_broker_partner_no_self_upline CHECK (upline_broker_id IS NULL OR upline_broker_id <> id);

CREATE INDEX ix_broker_partner_upline ON broker_partner(upline_broker_id) WHERE deleted_at IS NULL;
