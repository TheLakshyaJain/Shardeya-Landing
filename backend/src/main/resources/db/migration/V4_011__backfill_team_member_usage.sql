-- The trigger just added only fires on FUTURE app_user inserts/deletes --
-- every org created in M0-M3 already has its one admin row with no
-- corresponding org_usage entry, which EntitlementService.currentUsage()
-- would otherwise read as 0 (not 1) until that org's next team-member
-- change. Backfill so quota enforcement is correct from the moment this
-- migration runs, not just for orgs created after it.
INSERT INTO org_usage (org_id, limit_key, scope_id, current_value, updated_at)
SELECT org_id, 'BUILDER_TEAM_MEMBERS', NULL, count(*), now()
FROM app_user
WHERE deleted_at IS NULL
GROUP BY org_id
ON CONFLICT (org_id, limit_key, (coalesce(scope_id, org_id)))
DO UPDATE SET current_value = EXCLUDED.current_value, updated_at = now();
