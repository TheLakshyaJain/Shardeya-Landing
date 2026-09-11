package com.shardeya.foundation.subscription;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

/**
 * Trigger-maintained (V2_013 org_usage triggers) — read-only from the
 * application's point of view. {@code scopeId} is a judgment call not in the
 * original doc schema: NULL for org-wide limits (BUILDER_PROJECTS), the
 * project's id for per-project limits (BUILDER_PLOTS_PER_PROJECT) — see the
 * migration's own comment for why a plain (org_id, limit_key) row can't
 * represent a per-project quota.
 */
@Entity
@Table(name = "org_usage")
public class OrgUsage {

    @Id
    private UUID id;

    @Column(name = "org_id", nullable = false)
    private UUID orgId;

    @Column(name = "limit_key", nullable = false, length = 40)
    private String limitKey;

    @Column(name = "scope_id")
    private UUID scopeId;

    @Column(name = "current_value", nullable = false)
    private int currentValue;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected OrgUsage() {
    }

    public UUID getOrgId() {
        return orgId;
    }

    public String getLimitKey() {
        return limitKey;
    }

    public UUID getScopeId() {
        return scopeId;
    }

    public int getCurrentValue() {
        return currentValue;
    }
}
