-- M-06 Notification Engine (01-DATA-MODEL.md §8 notification / §22).
-- title_key/body_key are i18n keys, not rendered strings -- CLAUDE.md rule
-- #14 ("never send a rendered error message from the backend") applies
-- equally here so the bell renders in whatever language the viewer is
-- currently using, not the language active when the event fired.
CREATE TABLE notification (
    id                UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    org_id            UUID NOT NULL REFERENCES organization(id),
    recipient_user_id UUID NOT NULL REFERENCES app_user(id),
    type_code         VARCHAR(60) NOT NULL REFERENCES notification_type(code),
    title_key         VARCHAR(120) NOT NULL,
    body_key          VARCHAR(120),
    params            JSONB NOT NULL DEFAULT '{}',
    entity_type       VARCHAR(60),
    entity_id         UUID,
    priority          SMALLINT NOT NULL DEFAULT 0,
    read_at           TIMESTAMPTZ,
    created_at        TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX ix_notif_unread ON notification(recipient_user_id, created_at DESC) WHERE read_at IS NULL;
CREATE INDEX ix_notif_org ON notification(org_id);

ALTER TABLE notification ENABLE ROW LEVEL SECURITY;
ALTER TABLE notification FORCE ROW LEVEL SECURITY;
CREATE POLICY tenant_isolation ON notification
    USING (org_id = NULLIF(current_setting('app.current_org', true), '')::uuid);
