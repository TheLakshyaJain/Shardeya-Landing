-- M-06 §22, second half (01-DATA-MODEL.md §8 message_delivery) -- audit +
-- billing record for every real (non-in-app) send attempt, one row per
-- channel dispatch. `notification_id` is nullable: buyer-facing WhatsApp
-- messages (§22.4) and the OTP/staff-invite SMS paths have no in-app
-- notification row to point at at all.
CREATE TYPE message_delivery_channel AS ENUM ('WHATSAPP', 'SMS', 'EMAIL');
CREATE TYPE message_delivery_status AS ENUM ('QUEUED', 'SENT', 'DELIVERED', 'READ', 'FAILED');

CREATE TABLE message_delivery (
    id                  UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    org_id              UUID NOT NULL REFERENCES organization(id),
    channel             message_delivery_channel NOT NULL,
    -- Last 4 digits / domain only -- this table is read by verification
    -- tooling and (eventually) a delivery-status UI; the full number/email
    -- doesn't need to live in a second place beyond app_user/plot_sale.
    recipient_masked    VARCHAR(120) NOT NULL,
    template_code       VARCHAR(60),
    provider            VARCHAR(40) NOT NULL,
    provider_message_id VARCHAR(120),
    status              message_delivery_status NOT NULL DEFAULT 'QUEUED',
    error_code          VARCHAR(120),
    cost_paise          INTEGER,
    sent_at             TIMESTAMPTZ,
    delivered_at        TIMESTAMPTZ,
    notification_id     UUID REFERENCES notification(id),
    created_at          TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX ix_msg_delivery_org ON message_delivery(org_id);
CREATE INDEX ix_msg_delivery_notification ON message_delivery(notification_id) WHERE notification_id IS NOT NULL;

ALTER TABLE message_delivery ENABLE ROW LEVEL SECURITY;
ALTER TABLE message_delivery FORCE ROW LEVEL SECURITY;
CREATE POLICY tenant_isolation ON message_delivery
    USING (org_id = NULLIF(current_setting('app.current_org', true), '')::uuid);
