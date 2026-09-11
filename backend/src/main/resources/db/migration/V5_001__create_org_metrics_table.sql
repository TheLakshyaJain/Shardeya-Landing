-- B-01 §3 dashboard aggregate. One row per org, write-through maintained by
-- triggers on the source tables (see V5_002) -- CLAUDE.md's own pitfall #1
-- ("never COUNT(*) for dashboard cards") is the entire reason this table
-- exists. total_revenue is a plain SUM(payment_record.amount) including
-- reversal rows (negative amounts) -- reversals net out naturally, no
-- special-case logic needed, same reasoning M3 already established for why
-- plot_sale.total_paid needs no reversal-specific trigger code either.
CREATE TABLE org_metrics (
    org_id           UUID PRIMARY KEY REFERENCES organization(id),
    total_projects   INTEGER NOT NULL DEFAULT 0,
    total_plots      INTEGER NOT NULL DEFAULT 0,
    available_plots  INTEGER NOT NULL DEFAULT 0,
    sold_plots       INTEGER NOT NULL DEFAULT 0,
    reserved_plots   INTEGER NOT NULL DEFAULT 0,
    active_leads     INTEGER NOT NULL DEFAULT 0,
    total_revenue    NUMERIC(19,2) NOT NULL DEFAULT 0,
    updated_at       TIMESTAMPTZ NOT NULL DEFAULT now()
);

ALTER TABLE org_metrics ENABLE ROW LEVEL SECURITY;
ALTER TABLE org_metrics FORCE ROW LEVEL SECURITY;
CREATE POLICY tenant_isolation ON org_metrics
    USING (org_id = NULLIF(current_setting('app.current_org', true), '')::uuid);
