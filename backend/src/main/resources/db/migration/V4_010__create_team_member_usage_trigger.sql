-- B-12 §7 team quota (Free = owner only, Pro = 3, Premium = 15;
-- "Invited-but-not-accepted users count"). Same fn_org_usage_bump trigger
-- pattern V2_013 already established for BUILDER_PROJECTS/
-- BUILDER_PLOTS_PER_PROJECT -- counts every non-soft-deleted app_user row,
-- regardless of INVITED/ACTIVE/INACTIVE status (only REMOVE, which sets
-- deleted_at, takes a seat back).
CREATE OR REPLACE FUNCTION fn_app_user_usage_trigger() RETURNS trigger AS $$
BEGIN
    IF TG_OP = 'INSERT' THEN
        PERFORM fn_org_usage_bump(NEW.org_id, 'BUILDER_TEAM_MEMBERS', NULL, 1);
    ELSIF TG_OP = 'UPDATE' THEN
        IF OLD.deleted_at IS NULL AND NEW.deleted_at IS NOT NULL THEN
            PERFORM fn_org_usage_bump(NEW.org_id, 'BUILDER_TEAM_MEMBERS', NULL, -1);
        ELSIF OLD.deleted_at IS NOT NULL AND NEW.deleted_at IS NULL THEN
            PERFORM fn_org_usage_bump(NEW.org_id, 'BUILDER_TEAM_MEMBERS', NULL, 1);
        END IF;
    END IF;
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER trg_app_user_usage_ins AFTER INSERT ON app_user FOR EACH ROW EXECUTE FUNCTION fn_app_user_usage_trigger();
CREATE TRIGGER trg_app_user_usage_upd AFTER UPDATE OF deleted_at ON app_user FOR EACH ROW EXECUTE FUNCTION fn_app_user_usage_trigger();
