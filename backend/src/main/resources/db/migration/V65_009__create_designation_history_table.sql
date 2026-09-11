-- 06-BROKER-NETWORK-ENGINE.md §11/§15 build-order step 7 -- an append-only
-- audit trail of every designation change (AUTOMATIC, this step; MANUAL,
-- future step 8). Column list matches §11 verbatim except builder_id ->
-- org_id, the same rename V65_001's own comment already applied for
-- designation_slab, for the identical reason (every other table in this
-- codebase uses org_id).
--
-- No deleted_at/updated_at, and DELETE is blocked outright (RULE below) --
-- same "immutable audit trail, corrections are new rows, not edits" shape
-- commission_release/commission_payment already use, applied here to a
-- pure history log rather than a money ledger.
CREATE TYPE designation_change_type AS ENUM ('AUTOMATIC', 'MANUAL');

CREATE TABLE designation_history (
    id                          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    org_id                      UUID NOT NULL REFERENCES organization(id),
    broker_id                   UUID NOT NULL REFERENCES broker_partner(id),
    previous_designation_id     UUID REFERENCES designation_slab(id),
    new_designation_id          UUID NOT NULL REFERENCES designation_slab(id),
    previous_rate                NUMERIC(19,2),
    new_rate                     NUMERIC(19,2) NOT NULL,
    change_type                  designation_change_type NOT NULL,
    reason                       TEXT,
    effective_at                 TIMESTAMPTZ NOT NULL DEFAULT now(),
    changed_by                   UUID REFERENCES app_user(id)
);

CREATE INDEX ix_designation_history_org ON designation_history(org_id);
CREATE INDEX ix_designation_history_broker ON designation_history(broker_id);

CREATE RULE no_delete_designation_history AS ON DELETE TO designation_history DO INSTEAD NOTHING;

ALTER TABLE designation_history ENABLE ROW LEVEL SECURITY;
ALTER TABLE designation_history FORCE ROW LEVEL SECURITY;
CREATE POLICY tenant_isolation ON designation_history
    USING (org_id = NULLIF(current_setting('app.current_org', true), '')::uuid);
