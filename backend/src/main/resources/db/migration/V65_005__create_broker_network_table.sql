-- 06-BROKER-NETWORK-ENGINE.md §10/§11/§38 -- closure table for efficient
-- recursive team-sales rollup and tree queries. Every DESIGNATION broker
-- gets a depth-0 self-row (ancestor = descendant = itself) plus one row
-- per real ancestor at whatever depth -- maintained transactionally by
-- BrokerNetworkService on every add/re-parent (never by a DB trigger --
-- the integrity validation, self/cycle/descendant checks, needs to run
-- in Java before any row is written, not react after the fact).
CREATE TABLE broker_network (
    ancestor_broker_id    UUID NOT NULL REFERENCES broker_partner(id),
    descendant_broker_id  UUID NOT NULL REFERENCES broker_partner(id),
    depth                 INTEGER NOT NULL,
    org_id                UUID NOT NULL REFERENCES organization(id),
    PRIMARY KEY (ancestor_broker_id, descendant_broker_id),
    CONSTRAINT ck_broker_network_depth_nonneg CHECK (depth >= 0)
);

-- The two directions this table is actually queried in: "all ancestors of
-- X" (walking up to freeze a commission chain) and "all descendants of X"
-- (recursive team-sales rollup) -- the PK already covers ancestor-first
-- lookups efficiently; this covers the descendant-first direction.
CREATE INDEX ix_broker_network_descendant ON broker_network(descendant_broker_id);

ALTER TABLE broker_network ENABLE ROW LEVEL SECURITY;
ALTER TABLE broker_network FORCE ROW LEVEL SECURITY;
CREATE POLICY tenant_isolation ON broker_network
    USING (org_id = NULLIF(current_setting('app.current_org', true), '')::uuid);
