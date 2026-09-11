-- B-02 §3: gallery images (and a record of cover/layout/brochure attachment
-- events) live here; project's own cover_media_id/layout_media_id/
-- brochure_media_id columns remain the fast-path pointer for rendering.
CREATE TABLE project_media (
    id           UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    org_id       UUID NOT NULL REFERENCES organization(id),
    project_id   UUID NOT NULL REFERENCES project(id),
    media_id     UUID NOT NULL REFERENCES media_asset(id),
    role         VARCHAR(20) NOT NULL, -- COVER | GALLERY | LAYOUT | BROCHURE
    sort_order   INTEGER NOT NULL DEFAULT 0,
    created_at   TIMESTAMPTZ NOT NULL DEFAULT now(),
    created_by   UUID REFERENCES app_user(id),
    updated_at   TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_by   UUID REFERENCES app_user(id),
    deleted_at   TIMESTAMPTZ,
    deleted_by   UUID REFERENCES app_user(id),
    version      BIGINT NOT NULL DEFAULT 0
);

CREATE INDEX ix_project_media_org ON project_media(org_id) WHERE deleted_at IS NULL;
CREATE INDEX ix_project_media_project ON project_media(project_id, role, sort_order) WHERE deleted_at IS NULL;

ALTER TABLE project_media ENABLE ROW LEVEL SECURITY;
ALTER TABLE project_media FORCE ROW LEVEL SECURITY;
CREATE POLICY tenant_isolation ON project_media
    USING (org_id = NULLIF(current_setting('app.current_org', true), '')::uuid);
