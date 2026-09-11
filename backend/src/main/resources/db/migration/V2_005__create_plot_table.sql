-- B-03 Plot Inventory & Interactive Grid (01-DATA-MODEL.md §4 plot / §12.3.1).
CREATE TYPE plot_status AS ENUM ('AVAILABLE', 'RESERVED', 'SOLD');
CREATE TYPE plot_facing AS ENUM ('N', 'S', 'E', 'W', 'NE', 'NW', 'SE', 'SW');

CREATE TABLE plot (
    id                 UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    org_id             UUID NOT NULL REFERENCES organization(id),
    project_id         UUID NOT NULL REFERENCES project(id),
    plot_number        VARCHAR(30) NOT NULL,
    -- Doc formula is `regexp_replace(plot_number,'[^A-Za-z0-9]','')` with no
    -- 'g' flag, which only strips the FIRST non-alphanumeric character —
    -- "A-1-2" would normalise to "A12-2", not "A12", defeating the entire
    -- point of the column (colliding "A-12"/"a12"/"A 12"). Added the 'g' flag.
    plot_number_norm   VARCHAR(30) GENERATED ALWAYS AS (upper(regexp_replace(plot_number, '[^A-Za-z0-9]', '', 'g'))) STORED,
    status             plot_status NOT NULL DEFAULT 'AVAILABLE',
    reserved_for       VARCHAR(150),
    reserved_until     DATE,
    size_value         NUMERIC(14,4) NOT NULL,
    size_unit          VARCHAR(16) NOT NULL,
    size_sqft          NUMERIC(14,4) NOT NULL,
    facing             plot_facing,
    price              NUMERIC(19,2) NOT NULL,
    price_per_unit     NUMERIC(19,4) GENERATED ALWAYS AS (price / NULLIF(size_value, 0)) STORED,
    is_garden          BOOLEAN NOT NULL DEFAULT false,
    is_corner          BOOLEAN NOT NULL DEFAULT false,
    is_hot             BOOLEAN NOT NULL DEFAULT false,
    remarks            TEXT,
    grid_row           INTEGER,
    grid_col           INTEGER,
    -- No FK yet: plot_sale doesn't exist until M3 (same pattern as
    -- organization.logo_media_id before M-05 landed — see 01-DATA-MODEL.md's
    -- own M0 note). Add the FK in M3's first plot_sale migration.
    current_sale_id    UUID,
    created_at         TIMESTAMPTZ NOT NULL DEFAULT now(),
    created_by         UUID REFERENCES app_user(id),
    updated_at         TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_by         UUID REFERENCES app_user(id),
    deleted_at         TIMESTAMPTZ,
    deleted_by         UUID REFERENCES app_user(id),
    version            BIGINT NOT NULL DEFAULT 0
);

CREATE INDEX ix_plot_org ON plot(org_id) WHERE deleted_at IS NULL;
CREATE UNIQUE INDEX ux_plot_number ON plot(project_id, plot_number_norm) WHERE deleted_at IS NULL;
CREATE UNIQUE INDEX ux_plot_cell ON plot(project_id, grid_row, grid_col) WHERE deleted_at IS NULL AND grid_row IS NOT NULL;
CREATE INDEX ix_plot_status ON plot(project_id, status) WHERE deleted_at IS NULL;
CREATE INDEX ix_plot_size ON plot(project_id, size_sqft) WHERE deleted_at IS NULL;
CREATE INDEX ix_plot_num_trgm ON plot USING GIN(plot_number gin_trgm_ops);
-- Covering index: the grid endpoint's whole payload comes from an
-- index-only scan (B-03 §3 "the entire grid payload is served from one
-- index-only scan").
CREATE INDEX ix_plot_grid ON plot(project_id, grid_row, grid_col)
    INCLUDE (plot_number, status, size_sqft, is_hot, price) WHERE deleted_at IS NULL;

ALTER TABLE plot ENABLE ROW LEVEL SECURITY;
ALTER TABLE plot FORCE ROW LEVEL SECURITY;
CREATE POLICY tenant_isolation ON plot
    USING (org_id = NULLIF(current_setting('app.current_org', true), '')::uuid);
