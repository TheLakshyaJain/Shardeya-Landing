CREATE TABLE refresh_token (
    id          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id     UUID NOT NULL REFERENCES app_user(id),
    token_hash  TEXT NOT NULL,
    family_id   UUID NOT NULL,
    issued_at   TIMESTAMPTZ NOT NULL DEFAULT now(),
    expires_at  TIMESTAMPTZ NOT NULL,
    revoked_at  TIMESTAMPTZ,
    replaced_by UUID REFERENCES refresh_token(id),
    user_agent  TEXT,
    ip          VARCHAR(45)
);

CREATE UNIQUE INDEX ux_refresh_token_hash ON refresh_token(token_hash);
CREATE INDEX ix_refresh_token_family ON refresh_token(family_id);
CREATE INDEX ix_refresh_token_user ON refresh_token(user_id) WHERE revoked_at IS NULL;

-- No org_id column and no RLS: this table is never listed per-org — every
-- access is a targeted lookup by token_hash (post-hash, pre-auth) or by a
-- user_id the service already trusts (post-auth), matching role_permission's
-- precedent for join/auth-plumbing tables in 01-DATA-MODEL.md.
