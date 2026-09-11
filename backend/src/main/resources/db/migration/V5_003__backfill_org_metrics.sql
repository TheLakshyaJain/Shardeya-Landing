-- Every org created before V5_002's triggers existed has real projects,
-- plots, leads, and payments that predate the triggers -- same backfill
-- necessity V4_011 already established for org_usage's team-member count.
INSERT INTO org_metrics (org_id, total_projects, total_plots, available_plots, sold_plots, reserved_plots, active_leads, total_revenue, updated_at)
SELECT
    o.id,
    COALESCE((SELECT COUNT(*) FROM project p WHERE p.org_id = o.id AND p.deleted_at IS NULL), 0),
    COALESCE((SELECT COUNT(*) FROM plot pl WHERE pl.org_id = o.id AND pl.deleted_at IS NULL), 0),
    COALESCE((SELECT COUNT(*) FROM plot pl WHERE pl.org_id = o.id AND pl.deleted_at IS NULL AND pl.status = 'AVAILABLE'), 0),
    COALESCE((SELECT COUNT(*) FROM plot pl WHERE pl.org_id = o.id AND pl.deleted_at IS NULL AND pl.status = 'SOLD'), 0),
    COALESCE((SELECT COUNT(*) FROM plot pl WHERE pl.org_id = o.id AND pl.deleted_at IS NULL AND pl.status = 'RESERVED'), 0),
    COALESCE((SELECT COUNT(*) FROM customer c WHERE c.org_id = o.id AND c.deleted_at IS NULL AND c.status NOT IN ('DEAL_CLOSED', 'LOST')), 0),
    COALESCE((SELECT SUM(pr.amount) FROM payment_record pr WHERE pr.org_id = o.id), 0),
    now()
FROM organization o
WHERE o.type = 'BUILDER'
ON CONFLICT (org_id) DO UPDATE SET
    total_projects = EXCLUDED.total_projects,
    total_plots = EXCLUDED.total_plots,
    available_plots = EXCLUDED.available_plots,
    sold_plots = EXCLUDED.sold_plots,
    reserved_plots = EXCLUDED.reserved_plots,
    active_leads = EXCLUDED.active_leads,
    total_revenue = EXCLUDED.total_revenue,
    updated_at = now();
