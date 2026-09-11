-- org_usage.current_value is trigger-maintained (01-DATA-MODEL.md §13):
-- BUILDER_PROJECTS counts non-deleted `project` rows per org (scope_id NULL);
-- BUILDER_PLOTS_PER_PROJECT counts non-deleted `plot` rows per project
-- (scope_id = project_id). Soft-delete/restore (deleted_at going non-NULL/NULL)
-- decrements/increments the same way a hard delete/insert would.
CREATE OR REPLACE FUNCTION fn_org_usage_bump(p_org_id UUID, p_limit_key VARCHAR, p_scope_id UUID, p_delta INT)
RETURNS void AS $$
BEGIN
    INSERT INTO org_usage (org_id, limit_key, scope_id, current_value, updated_at)
    VALUES (p_org_id, p_limit_key, p_scope_id, GREATEST(p_delta, 0), now())
    ON CONFLICT (org_id, limit_key, (coalesce(scope_id, org_id)))
    DO UPDATE SET current_value = GREATEST(org_usage.current_value + p_delta, 0), updated_at = now();
END;
$$ LANGUAGE plpgsql;

CREATE OR REPLACE FUNCTION fn_project_usage_trigger() RETURNS trigger AS $$
BEGIN
    IF TG_OP = 'INSERT' THEN
        PERFORM fn_org_usage_bump(NEW.org_id, 'BUILDER_PROJECTS', NULL, 1);
    ELSIF TG_OP = 'UPDATE' THEN
        IF OLD.deleted_at IS NULL AND NEW.deleted_at IS NOT NULL THEN
            PERFORM fn_org_usage_bump(NEW.org_id, 'BUILDER_PROJECTS', NULL, -1);
        ELSIF OLD.deleted_at IS NOT NULL AND NEW.deleted_at IS NULL THEN
            PERFORM fn_org_usage_bump(NEW.org_id, 'BUILDER_PROJECTS', NULL, 1);
        END IF;
    END IF;
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER trg_project_usage_ins AFTER INSERT ON project FOR EACH ROW EXECUTE FUNCTION fn_project_usage_trigger();
CREATE TRIGGER trg_project_usage_upd AFTER UPDATE OF deleted_at ON project FOR EACH ROW EXECUTE FUNCTION fn_project_usage_trigger();

CREATE OR REPLACE FUNCTION fn_plot_usage_trigger() RETURNS trigger AS $$
BEGIN
    IF TG_OP = 'INSERT' THEN
        PERFORM fn_org_usage_bump(NEW.org_id, 'BUILDER_PLOTS_PER_PROJECT', NEW.project_id, 1);
    ELSIF TG_OP = 'UPDATE' THEN
        IF OLD.deleted_at IS NULL AND NEW.deleted_at IS NOT NULL THEN
            PERFORM fn_org_usage_bump(NEW.org_id, 'BUILDER_PLOTS_PER_PROJECT', NEW.project_id, -1);
        ELSIF OLD.deleted_at IS NOT NULL AND NEW.deleted_at IS NULL THEN
            PERFORM fn_org_usage_bump(NEW.org_id, 'BUILDER_PLOTS_PER_PROJECT', NEW.project_id, 1);
        END IF;
    END IF;
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER trg_plot_usage_ins AFTER INSERT ON plot FOR EACH ROW EXECUTE FUNCTION fn_plot_usage_trigger();
CREATE TRIGGER trg_plot_usage_upd AFTER UPDATE OF deleted_at ON plot FOR EACH ROW EXECUTE FUNCTION fn_plot_usage_trigger();
