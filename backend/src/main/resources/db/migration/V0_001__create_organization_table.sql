CREATE TYPE org_type AS ENUM ('BROKER', 'BUILDER');
CREATE TYPE org_status AS ENUM ('ACTIVE', 'SUSPENDED', 'PENDING_DELETION');

CREATE TABLE organization (
    id                     UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    type                   org_type NOT NULL,
    name                   VARCHAR(150) NOT NULL,
    city                   VARCHAR(100) NOT NULL,
    state_code             CHAR(2),
    logo_media_id          UUID,
    default_language       CHAR(2) NOT NULL DEFAULT 'en',
    timezone               VARCHAR(40) NOT NULL DEFAULT 'Asia/Kolkata',
    notification_hour      SMALLINT NOT NULL DEFAULT 9,
    status                 org_status NOT NULL DEFAULT 'ACTIVE',
    deletion_requested_at  TIMESTAMPTZ,
    created_at             TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at             TIMESTAMPTZ NOT NULL DEFAULT now()
);

-- organization is the tenant root itself (no org_id column) — not subject to RLS.
