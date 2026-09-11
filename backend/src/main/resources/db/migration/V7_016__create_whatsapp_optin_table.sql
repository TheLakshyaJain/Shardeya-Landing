-- M-06 §22.1/§22.4 (01-DATA-MODEL.md §8 whatsapp_optin) -- explicit opt-in
-- is mandatory before any WhatsApp send, for both internal users (staff
-- opting their own mobile in via /settings) and buyers (third parties;
-- consent captured by the builder on their behalf, source='BUILDER_CAPTURED').
-- One row per (org, mobile) tracks current state -- opted_out_at is set (not
-- a new row inserted) when a "STOP" reply comes back, so a re-opt-in later
-- is just opted_out_at going back to NULL, not a growing history table.
CREATE TABLE whatsapp_optin (
    id             UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    org_id         UUID NOT NULL REFERENCES organization(id),
    mobile         VARCHAR(15) NOT NULL,
    opted_in_at    TIMESTAMPTZ NOT NULL DEFAULT now(),
    opted_out_at   TIMESTAMPTZ,
    source         VARCHAR(40) NOT NULL,
    created_at     TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at     TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE UNIQUE INDEX ux_whatsapp_optin_org_mobile ON whatsapp_optin(org_id, mobile);

ALTER TABLE whatsapp_optin ENABLE ROW LEVEL SECURITY;
ALTER TABLE whatsapp_optin FORCE ROW LEVEL SECURITY;
CREATE POLICY tenant_isolation ON whatsapp_optin
    USING (org_id = NULLIF(current_setting('app.current_org', true), '')::uuid);
