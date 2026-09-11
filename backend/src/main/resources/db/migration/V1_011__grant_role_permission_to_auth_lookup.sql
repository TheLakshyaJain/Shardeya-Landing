-- V1_009 granted shardeya_authlookup SELECT on app_user and role, but
-- AuthLookupRepository's lookup query also reads role_permission (a
-- correlated subquery pulling the role's permission codes, to avoid a
-- second round trip for JWT issuance) — that grant was missed. Never
-- surfaced by any repository-layer test (those all run through the main
-- shardeya_app connection, which owns full access) or by mocked-service
-- tests; only found by driving login through a real HTTP call against a
-- real docker-compose Postgres, where it failed as "permission denied for
-- table role_permission".
GRANT SELECT ON role_permission TO shardeya_authlookup;
