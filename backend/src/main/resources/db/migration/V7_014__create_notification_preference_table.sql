-- M-06 §22, second half: per-user per-type channel toggles
-- (01-DATA-MODEL.md §8 notification_preference). Sparse by design: a row
-- only exists once a user has actually overridden a type's defaults --
-- NotificationDispatchService falls back to notification_type's own
-- default_channels/is_mandatory when no row exists for (org, user, type),
-- so most users never accumulate any rows here at all.
CREATE TABLE notification_preference (
    id         UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    org_id     UUID NOT NULL REFERENCES organization(id),
    user_id    UUID NOT NULL REFERENCES app_user(id),
    type_code  VARCHAR(60) NOT NULL REFERENCES notification_type(code),
    in_app     BOOLEAN NOT NULL DEFAULT true,
    whatsapp   BOOLEAN NOT NULL DEFAULT true,
    sms        BOOLEAN NOT NULL DEFAULT true,
    email      BOOLEAN NOT NULL DEFAULT true,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

-- One override row per user per type -- the PUT /settings/notifications
-- bulk-update endpoint upserts on this, never inserts a duplicate.
CREATE UNIQUE INDEX ux_notif_pref_user_type ON notification_preference(user_id, type_code);
CREATE INDEX ix_notif_pref_org ON notification_preference(org_id);

ALTER TABLE notification_preference ENABLE ROW LEVEL SECURITY;
ALTER TABLE notification_preference FORCE ROW LEVEL SECURITY;
CREATE POLICY tenant_isolation ON notification_preference
    USING (org_id = NULLIF(current_setting('app.current_org', true), '')::uuid);
