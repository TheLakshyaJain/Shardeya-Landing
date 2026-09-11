package com.shardeya.foundation.rbac;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.hibernate.annotations.CreationTimestamp;

import java.time.Instant;
import java.util.UUID;

/**
 * M-02 §3 / B-12 §18.2: a row exists only for a user whose
 * {@code project_access_mode = SCOPED}. Applied as a hard predicate by
 * {@link com.shardeya.platform.ProjectAccessGuard} — see that class.
 */
@Entity
@Table(name = "user_project_access")
public class UserProjectAccess {

    @Id
    private UUID id;

    @Column(name = "org_id", nullable = false)
    private UUID orgId;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Column(name = "project_id", nullable = false)
    private UUID projectId;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "created_by")
    private UUID createdBy;

    protected UserProjectAccess() {
    }

    public UserProjectAccess(UUID id, UUID orgId, UUID userId, UUID projectId) {
        this.id = id;
        this.orgId = orgId;
        this.userId = userId;
        this.projectId = projectId;
    }

    public UUID getId() {
        return id;
    }

    public UUID getOrgId() {
        return orgId;
    }

    public UUID getUserId() {
        return userId;
    }

    public UUID getProjectId() {
        return projectId;
    }

    public UUID getCreatedBy() {
        return createdBy;
    }

    public void setCreatedBy(UUID createdBy) {
        this.createdBy = createdBy;
    }
}
