-- org_metrics write-through maintenance, mirroring V2_013's fn_org_usage_bump
-- pattern (upsert-on-first-touch, then increment/decrement in place) but
-- against org_metrics' named-column shape rather than a generic
-- (limit_key, current_value) row, since B-01 §3 specifies org_metrics as a
-- wide table with one column per dashboard card, not a key-value table.

CREATE OR REPLACE FUNCTION fn_org_metrics_touch(p_org_id UUID) RETURNS void AS $$
BEGIN
    INSERT INTO org_metrics (org_id) VALUES (p_org_id) ON CONFLICT (org_id) DO NOTHING;
    UPDATE org_metrics SET updated_at = now() WHERE org_id = p_org_id;
END;
$$ LANGUAGE plpgsql;

-- ---------------------------------------------------------------- project
CREATE OR REPLACE FUNCTION fn_org_metrics_project_trigger() RETURNS trigger AS $$
BEGIN
    PERFORM fn_org_metrics_touch(NEW.org_id);
    IF TG_OP = 'INSERT' THEN
        UPDATE org_metrics SET total_projects = total_projects + 1 WHERE org_id = NEW.org_id;
    ELSIF TG_OP = 'UPDATE' THEN
        IF OLD.deleted_at IS NULL AND NEW.deleted_at IS NOT NULL THEN
            UPDATE org_metrics SET total_projects = total_projects - 1 WHERE org_id = NEW.org_id;
        ELSIF OLD.deleted_at IS NOT NULL AND NEW.deleted_at IS NULL THEN
            UPDATE org_metrics SET total_projects = total_projects + 1 WHERE org_id = NEW.org_id;
        END IF;
    END IF;
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER trg_org_metrics_project_ins AFTER INSERT ON project FOR EACH ROW EXECUTE FUNCTION fn_org_metrics_project_trigger();
CREATE TRIGGER trg_org_metrics_project_upd AFTER UPDATE OF deleted_at ON project FOR EACH ROW EXECUTE FUNCTION fn_org_metrics_project_trigger();

-- ------------------------------------------------------------------- plot
-- total_plots tracks existence (deleted_at); available/sold/reserved track
-- the current status bucket independently, since a plot moves between
-- buckets (via the application-service single write path per
-- 01-DATA-MODEL.md §13 -- plot.status is never touched directly by a
-- trigger elsewhere) without its existence changing.
CREATE OR REPLACE FUNCTION fn_org_metrics_status_bump(p_org_id UUID, p_status plot_status, p_delta INT) RETURNS void AS $$
BEGIN
    IF p_status = 'AVAILABLE' THEN
        UPDATE org_metrics SET available_plots = available_plots + p_delta WHERE org_id = p_org_id;
    ELSIF p_status = 'SOLD' THEN
        UPDATE org_metrics SET sold_plots = sold_plots + p_delta WHERE org_id = p_org_id;
    ELSIF p_status = 'RESERVED' THEN
        UPDATE org_metrics SET reserved_plots = reserved_plots + p_delta WHERE org_id = p_org_id;
    END IF;
END;
$$ LANGUAGE plpgsql;

CREATE OR REPLACE FUNCTION fn_org_metrics_plot_trigger() RETURNS trigger AS $$
BEGIN
    PERFORM fn_org_metrics_touch(NEW.org_id);
    IF TG_OP = 'INSERT' THEN
        UPDATE org_metrics SET total_plots = total_plots + 1 WHERE org_id = NEW.org_id;
        PERFORM fn_org_metrics_status_bump(NEW.org_id, NEW.status, 1);
    ELSIF TG_OP = 'UPDATE' THEN
        IF OLD.deleted_at IS NULL AND NEW.deleted_at IS NOT NULL THEN
            UPDATE org_metrics SET total_plots = total_plots - 1 WHERE org_id = NEW.org_id;
            PERFORM fn_org_metrics_status_bump(NEW.org_id, OLD.status, -1);
        ELSIF OLD.deleted_at IS NOT NULL AND NEW.deleted_at IS NULL THEN
            UPDATE org_metrics SET total_plots = total_plots + 1 WHERE org_id = NEW.org_id;
            PERFORM fn_org_metrics_status_bump(NEW.org_id, NEW.status, 1);
        ELSIF NEW.deleted_at IS NULL AND OLD.status IS DISTINCT FROM NEW.status THEN
            PERFORM fn_org_metrics_status_bump(NEW.org_id, OLD.status, -1);
            PERFORM fn_org_metrics_status_bump(NEW.org_id, NEW.status, 1);
        END IF;
    END IF;
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER trg_org_metrics_plot_ins AFTER INSERT ON plot FOR EACH ROW EXECUTE FUNCTION fn_org_metrics_plot_trigger();
CREATE TRIGGER trg_org_metrics_plot_upd AFTER UPDATE OF deleted_at, status ON plot FOR EACH ROW EXECUTE FUNCTION fn_org_metrics_plot_trigger();

-- --------------------------------------------------------------- customer
-- active_leads excludes terminal statuses (DEAL_CLOSED/LOST), matching
-- CustomerService's own TERMINAL_STATUSES constant exactly -- kept in sync
-- by hand since a DB enum can't reference a Java constant; if a future
-- terminal status is ever added there, add it here too.
CREATE OR REPLACE FUNCTION fn_org_metrics_lead_active(p_status lead_status, p_deleted_at TIMESTAMPTZ) RETURNS BOOLEAN AS $$
BEGIN
    RETURN p_deleted_at IS NULL AND p_status NOT IN ('DEAL_CLOSED', 'LOST');
END;
$$ LANGUAGE plpgsql IMMUTABLE;

CREATE OR REPLACE FUNCTION fn_org_metrics_customer_trigger() RETURNS trigger AS $$
DECLARE
    was_active BOOLEAN;
    is_active BOOLEAN;
BEGIN
    PERFORM fn_org_metrics_touch(NEW.org_id);
    IF TG_OP = 'INSERT' THEN
        IF fn_org_metrics_lead_active(NEW.status, NEW.deleted_at) THEN
            UPDATE org_metrics SET active_leads = active_leads + 1 WHERE org_id = NEW.org_id;
        END IF;
    ELSIF TG_OP = 'UPDATE' THEN
        was_active := fn_org_metrics_lead_active(OLD.status, OLD.deleted_at);
        is_active := fn_org_metrics_lead_active(NEW.status, NEW.deleted_at);
        IF was_active AND NOT is_active THEN
            UPDATE org_metrics SET active_leads = active_leads - 1 WHERE org_id = NEW.org_id;
        ELSIF NOT was_active AND is_active THEN
            UPDATE org_metrics SET active_leads = active_leads + 1 WHERE org_id = NEW.org_id;
        END IF;
    END IF;
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER trg_org_metrics_customer_ins AFTER INSERT ON customer FOR EACH ROW EXECUTE FUNCTION fn_org_metrics_customer_trigger();
CREATE TRIGGER trg_org_metrics_customer_upd AFTER UPDATE OF deleted_at, status ON customer FOR EACH ROW EXECUTE FUNCTION fn_org_metrics_customer_trigger();

-- ---------------------------------------------------------- payment_record
-- payment_record is append-only (a DB RULE blocks DELETE, no deleted_at
-- column -- M3's own documented immutability contract), so a plain AFTER
-- INSERT summing NEW.amount is the whole trigger: reversal rows are already
-- negative amounts, so they net out on their own with no reversal-specific
-- branch needed here, same reasoning M3 established for plot_sale.total_paid.
CREATE OR REPLACE FUNCTION fn_org_metrics_payment_trigger() RETURNS trigger AS $$
BEGIN
    PERFORM fn_org_metrics_touch(NEW.org_id);
    UPDATE org_metrics SET total_revenue = total_revenue + NEW.amount WHERE org_id = NEW.org_id;
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER trg_org_metrics_payment_ins AFTER INSERT ON payment_record FOR EACH ROW EXECUTE FUNCTION fn_org_metrics_payment_trigger();
