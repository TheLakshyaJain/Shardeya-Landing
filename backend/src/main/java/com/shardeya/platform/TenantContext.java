package com.shardeya.platform;

import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * Per-request tenancy facts (org_id, user_id, role, permissions, project scope),
 * populated by {@link TenantContextFilter} from the validated JWT. Layer 1 of
 * the three-layer tenant isolation model (00-ARCHITECTURE.md §2.2). Only
 * {@code platform.*} may reference this class directly (ArchUnit-enforced) —
 * everything else goes through the repository base class, {@code @RequiresPermission},
 * or {@code @ScopedToProject}.
 */
public final class TenantContext {

    public record Tenant(
            UUID orgId, UUID userId, String orgType, String role, Set<String> permissions,
            List<UUID> projectScope, boolean allProjects) {
    }

    private static final ThreadLocal<Tenant> CURRENT = new ThreadLocal<>();

    private TenantContext() {
    }

    static void set(Tenant tenant) {
        CURRENT.set(tenant);
    }

    public static Tenant current() {
        Tenant tenant = CURRENT.get();
        if (tenant == null) {
            throw new IllegalStateException("No tenant context bound to this thread");
        }
        return tenant;
    }

    public static UUID currentOrgId() {
        return current().orgId();
    }

    /** For code that must work both inside and outside a tenant-bound request (e.g. the connection-tagging DataSource wrapper). */
    public static Tenant currentOrNull() {
        return CURRENT.get();
    }

    static void clear() {
        CURRENT.remove();
    }
}
