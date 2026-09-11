-- B-12 §3. Staff members are created as app_user rows with status=INVITED
-- and no password_hash; this table tracks the single-use set-password link
-- (token_hash, never the raw token -- same "never store the secret itself"
-- pattern as refresh_token.token_hash and otp_challenge.code_hash).
CREATE TABLE staff_invite (
    id            UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    org_id        UUID NOT NULL REFERENCES organization(id),
    app_user_id   UUID NOT NULL REFERENCES app_user(id),
    token_hash    TEXT NOT NULL,
    channel       VARCHAR(10) NOT NULL,
    sent_at       TIMESTAMPTZ,
    expires_at    TIMESTAMPTZ NOT NULL,
    accepted_at   TIMESTAMPTZ,
    resend_count  SMALLINT NOT NULL DEFAULT 0,
    created_at    TIMESTAMPTZ NOT NULL DEFAULT now(),
    created_by    UUID REFERENCES app_user(id),
    updated_at    TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_by    UUID REFERENCES app_user(id),
    deleted_at    TIMESTAMPTZ,
    deleted_by    UUID REFERENCES app_user(id),
    version       BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT chk_staff_invite_channel CHECK (channel IN ('SMS', 'EMAIL'))
);

CREATE INDEX ix_staff_invite_org ON staff_invite(org_id) WHERE deleted_at IS NULL;
CREATE INDEX ix_staff_invite_user ON staff_invite(app_user_id) WHERE deleted_at IS NULL;
-- Looked up by hash on the accept-invite page; only ever one *active*
-- (unaccepted, unexpired-in-spirit -- expiry is checked in application code
-- so a clear message can be shown) invite matters for a lookup.
CREATE INDEX ix_staff_invite_token_hash ON staff_invite(token_hash) WHERE deleted_at IS NULL;

ALTER TABLE staff_invite ENABLE ROW LEVEL SECURITY;
ALTER TABLE staff_invite FORCE ROW LEVEL SECURITY;
CREATE POLICY tenant_isolation ON staff_invite
    USING (org_id = NULLIF(current_setting('app.current_org', true), '')::uuid);
