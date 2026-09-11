-- M-13 Audit & Privacy (01-DATA-MODEL.md §12 sensitive_access_log) is a
-- later milestone in full (audit_log, platform_access_log, data export/
-- deletion requests), but B-04 §7/§9 requires gov-ID reveal to be audited
-- RIGHT NOW ("Reveal is a separate permissioned, audited call"). Building
-- just this one table ahead of the rest of M-13 mirrors M1's own precedent:
-- OutboxEvent shipped as "a minimal... outbox, just enough to prove the
-- pattern... NOT the full M-06... extend when M-06 is actually built."
-- Same shape here -- extend into the full audit_log/platform_access_log
-- set when M-13 itself is built.
CREATE TABLE sensitive_access_log (
    id             UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    org_id         UUID NOT NULL REFERENCES organization(id),
    actor_user_id  UUID NOT NULL REFERENCES app_user(id),
    entity_type    VARCHAR(60) NOT NULL,
    entity_id      UUID NOT NULL,
    field          VARCHAR(60) NOT NULL,
    reason         TEXT,
    occurred_at    TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX ix_sensitive_access_log_org ON sensitive_access_log(org_id);
CREATE INDEX ix_sensitive_access_log_entity ON sensitive_access_log(entity_type, entity_id);

-- Immutable -- an access log that can be edited or deleted proves nothing.
CREATE RULE no_delete_sensitive_access_log AS ON DELETE TO sensitive_access_log DO INSTEAD NOTHING;
CREATE RULE no_update_sensitive_access_log AS ON UPDATE TO sensitive_access_log DO INSTEAD NOTHING;

ALTER TABLE sensitive_access_log ENABLE ROW LEVEL SECURITY;
ALTER TABLE sensitive_access_log FORCE ROW LEVEL SECURITY;
CREATE POLICY tenant_isolation ON sensitive_access_log
    USING (org_id = NULLIF(current_setting('app.current_org', true), '')::uuid);
