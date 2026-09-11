-- 06-BROKER-NETWORK-ENGINE.md §5/§11 -- designation_slab, the 8-level
-- rate table for DESIGNATION-type brokers. MUST be config data, not
-- hardcoded if/else (§47), so the builder can tune it later without a
-- code change.
--
-- Naming note: the spec doc calls the tenant column "builder_id", but
-- every other tenant-scoped table in this schema (including
-- broker_partner itself) uses "org_id" -- kept "org_id" here for
-- consistency with that universal convention rather than introducing a
-- one-off name.
--
-- Nullable org_id (NULL = system-wide default), same shape as
-- role.org_id (V0_002): every tenant sees the system-default rows, and a
-- future per-org override capability (spec's own "the builder may want
-- to tune them") can add org-specific rows without a schema change. No
-- v1 UI/API writes an org-specific row yet -- see CLAUDE.md's own note on
-- this milestone for why.
CREATE TABLE designation_slab (
    id                UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    org_id            UUID REFERENCES organization(id),
    name              VARCHAR(60) NOT NULL,
    name_hi           VARCHAR(60),
    min_team_sales    INTEGER NOT NULL,
    max_team_sales    INTEGER,
    rate_per_sqft     NUMERIC(19,2) NOT NULL,
    sort_order        SMALLINT NOT NULL DEFAULT 0,
    is_active         BOOLEAN NOT NULL DEFAULT true,
    created_at        TIMESTAMPTZ NOT NULL DEFAULT now(),
    created_by        UUID REFERENCES app_user(id),
    updated_at        TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_by        UUID REFERENCES app_user(id),
    deleted_at        TIMESTAMPTZ,
    deleted_by        UUID REFERENCES app_user(id),
    version           BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT ck_designation_slab_range CHECK (max_team_sales IS NULL OR max_team_sales >= min_team_sales),
    CONSTRAINT ck_designation_slab_min_nonneg CHECK (min_team_sales >= 0),
    CONSTRAINT ck_designation_slab_rate_nonneg CHECK (rate_per_sqft >= 0)
);

CREATE INDEX ix_designation_slab_org ON designation_slab(org_id) WHERE deleted_at IS NULL;

-- Named uniquely so the seed migration (V65_002) can target it with
-- ON CONFLICT for real idempotency -- a bare "ON CONFLICT DO NOTHING"
-- with no matching constraint silently inserts duplicates on a second
-- run instead of skipping them. org_id is nullable, so this uses
-- COALESCE to a fixed sentinel rather than a plain column in the index
-- expression (a plain UNIQUE index on a nullable column never
-- conflicts across multiple NULLs, same reasoning as the EXCLUDE
-- constraint's own NULL behavior noted above).
CREATE UNIQUE INDEX ux_designation_slab_org_name ON designation_slab(COALESCE(org_id, '00000000-0000-0000-0000-000000000000'::uuid), name)
    WHERE deleted_at IS NULL;

-- Overlap-prevention, exact same pattern V6_002 (broker_tier) already
-- established for tier ranges. IMPORTANT: EXCLUDE constraints never
-- consider two NULLs "equal" for the `=` operator (same rule as UNIQUE),
-- so this constraint does NOT by itself prevent two overlapping
-- system-default (org_id IS NULL) rows from coexisting -- it only
-- protects per-org custom rows from overlapping each other (and, once a
-- per-org override API exists, from overlapping within that org). The
-- system-default 8 rows are seeded once, by us, in V65_002, and their
-- non-overlap is our own responsibility to get right at seed time (the
-- same trust boundary stamp_duty_rate/measurement_unit's own seed data
-- already relies on) -- there is no v1 write path that could ever
-- introduce a second NULL-org_id row to begin with.
ALTER TABLE designation_slab ADD CONSTRAINT ex_designation_slab_no_overlap
    EXCLUDE USING gist (
        org_id WITH =,
        int4range(min_team_sales, max_team_sales + 1) WITH &&
    ) WHERE (deleted_at IS NULL);

ALTER TABLE designation_slab ENABLE ROW LEVEL SECURITY;
ALTER TABLE designation_slab FORCE ROW LEVEL SECURITY;
CREATE POLICY tenant_isolation ON designation_slab
    USING (org_id IS NULL OR org_id = NULLIF(current_setting('app.current_org', true), '')::uuid);
