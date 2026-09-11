CREATE TYPE outbox_status AS ENUM ('PENDING', 'PROCESSING', 'DONE', 'FAILED');

CREATE TABLE outbox_event (
    id             UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    aggregate_type VARCHAR(60) NOT NULL,
    aggregate_id   UUID NOT NULL,
    event_type     VARCHAR(60) NOT NULL,
    payload        JSONB NOT NULL,
    available_at   TIMESTAMPTZ NOT NULL DEFAULT now(),
    attempts       SMALLINT NOT NULL DEFAULT 0,
    status         outbox_status NOT NULL DEFAULT 'PENDING',
    last_error     TEXT,
    created_at     TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX ix_outbox_ready ON outbox_event(available_at) WHERE status = 'PENDING';

-- No org_id: this is internal system plumbing (the poller scans across all
-- tenants), never queried per-org, so RLS doesn't apply — same as measurement
-- reference tables.
