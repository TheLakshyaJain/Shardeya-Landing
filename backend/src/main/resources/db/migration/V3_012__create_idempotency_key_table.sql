-- B-04/B-05 API contract: POST /plots/{plotId}/sale and POST
-- /sales/{id}/payments both take an Idempotency-Key header. A retried
-- request (network blip, double-click, or "two staff recording the same
-- payment simultaneously" per B-05 §10) with the same key replays the
-- original response instead of creating a second sale/payment.
CREATE TABLE idempotency_key (
    org_id           UUID NOT NULL REFERENCES organization(id),
    idempotency_key  VARCHAR(200) NOT NULL,
    endpoint         VARCHAR(120) NOT NULL,
    response_status  INTEGER NOT NULL,
    response_body    JSONB NOT NULL,
    created_at       TIMESTAMPTZ NOT NULL DEFAULT now(),
    PRIMARY KEY (org_id, idempotency_key, endpoint)
);

ALTER TABLE idempotency_key ENABLE ROW LEVEL SECURITY;
ALTER TABLE idempotency_key FORCE ROW LEVEL SECURITY;
CREATE POLICY tenant_isolation ON idempotency_key
    USING (org_id = NULLIF(current_setting('app.current_org', true), '')::uuid);
