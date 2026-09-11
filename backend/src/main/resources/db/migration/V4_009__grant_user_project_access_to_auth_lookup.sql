-- Same reasoning as V1_011 (which granted role_permission for the same
-- query): login/refresh mint an access token carrying real project scope
-- now (M4), and that scope has to be read in the same BYPASSRLS
-- pre-tenant-context lookup query as everything else in AuthLookupUser.
GRANT SELECT ON user_project_access TO shardeya_authlookup;
