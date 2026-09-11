package com.shardeya.foundation.auth;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Everything the login/signup flow needs before it has org context, fetched
 * via the BYPASSRLS {@code shardeya_authlookup} connection in one query. Not
 * a JPA entity — deliberately a plain projection so it's obvious this bypasses
 * normal RLS-protected repository access. Includes permissions (a correlated
 * array_agg over role_permission in the same query) specifically so refresh
 * -token rotation can mint a new access token without needing to bind tenant
 * context and touch the RLS-protected path at all for what's otherwise a
 * read-only, high-frequency operation.
 */
public record AuthLookupUser(
        UUID id,
        UUID orgId,
        String mobile,
        String email,
        String passwordHash,
        short failedLoginCount,
        Instant lockedUntil,
        String status,
        short tokenVersion,
        String fullName,
        String language,
        boolean owner,
        UUID roleId,
        String roleCode,
        List<String> permissions,
        boolean allProjects,
        List<UUID> projectScope) {
}
