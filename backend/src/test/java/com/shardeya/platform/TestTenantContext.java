package com.shardeya.platform;

import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * {@link TenantContext#set}/{@code clear} are package-private by design (only
 * {@code platform.*} should bind tenant context in production code). Tests
 * outside this package still legitimately need to set up fixtures under a
 * given org, so this is a public bridge that exists in test sources only.
 */
public final class TestTenantContext {

    private TestTenantContext() {
    }

    public static void bind(UUID orgId, UUID userId, String orgType, String role, Set<String> permissions) {
        TenantContext.set(new TenantContext.Tenant(orgId, userId, orgType, role, permissions, List.of(), true));
    }

    public static void clear() {
        TenantContext.clear();
    }
}
