-- M-11 Calendar & Reminder Engine (01-DATA-MODEL.md §7). Most rows are
-- projections, not user-entered data: the unique index below on
-- (source_entity_type, source_entity_id, event_type) WHERE source='AUTO' is
-- what makes upserting a projection idempotent -- changing a customer's
-- follow_up_date (or a payment_schedule's due_date) updates the SAME row
-- instead of creating a duplicate, by design (CalendarService always does
-- an upsert keyed on this index, never a plain insert, for AUTO events).
CREATE TYPE calendar_event_type AS ENUM ('FOLLOW_UP', 'SITE_VISIT', 'INSTALMENT_DUE', 'MANUAL_MEETING', 'IMPORTANT_DATE');
CREATE TYPE calendar_event_source AS ENUM ('MANUAL', 'AUTO');
CREATE TYPE calendar_source_entity_type AS ENUM ('CUSTOMER', 'PAYMENT_SCHEDULE', 'DEAL');
CREATE TYPE calendar_event_status AS ENUM ('SCHEDULED', 'DONE', 'CANCELLED');

CREATE TABLE calendar_event (
    id                   UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    org_id               UUID NOT NULL REFERENCES organization(id),
    title                VARCHAR(150) NOT NULL,
    event_date           DATE NOT NULL,
    event_time           TIME,
    duration_minutes     SMALLINT,
    event_type           calendar_event_type NOT NULL,
    source               calendar_event_source NOT NULL,
    source_entity_type   calendar_source_entity_type,
    source_entity_id     UUID,
    customer_id          UUID REFERENCES customer(id),
    property_id          UUID,
    project_id           UUID REFERENCES project(id),
    plot_id              UUID REFERENCES plot(id),
    assigned_to          UUID REFERENCES app_user(id),
    notes                TEXT,
    reminder_enabled     BOOLEAN NOT NULL DEFAULT true,
    reminder_offsets     INT[] NOT NULL DEFAULT '{0,1}',
    status               calendar_event_status NOT NULL DEFAULT 'SCHEDULED',
    created_at           TIMESTAMPTZ NOT NULL DEFAULT now(),
    created_by           UUID REFERENCES app_user(id),
    updated_at           TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_by           UUID REFERENCES app_user(id),
    deleted_at           TIMESTAMPTZ,
    deleted_by           UUID REFERENCES app_user(id),
    version              BIGINT NOT NULL DEFAULT 0
);

CREATE INDEX ix_cal_org ON calendar_event(org_id) WHERE deleted_at IS NULL;
CREATE INDEX ix_cal_date ON calendar_event(org_id, event_date) WHERE deleted_at IS NULL;
CREATE INDEX ix_cal_assigned ON calendar_event(org_id, assigned_to) WHERE deleted_at IS NULL;

CREATE UNIQUE INDEX ux_cal_auto_projection
    ON calendar_event(source_entity_type, source_entity_id, event_type)
    WHERE source = 'AUTO' AND deleted_at IS NULL;

ALTER TABLE calendar_event ENABLE ROW LEVEL SECURITY;
ALTER TABLE calendar_event FORCE ROW LEVEL SECURITY;
CREATE POLICY tenant_isolation ON calendar_event
    USING (org_id = NULLIF(current_setting('app.current_org', true), '')::uuid);
