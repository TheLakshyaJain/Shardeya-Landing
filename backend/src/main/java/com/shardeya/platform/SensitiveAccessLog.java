package com.shardeya.platform;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.hibernate.annotations.CreationTimestamp;

import java.time.Instant;
import java.util.UUID;

/**
 * Minimal slice of the future M-13 Audit &amp; Privacy module (01-DATA-MODEL.md
 * §12), built now because B-04 requires gov-ID reveal to be audited
 * immediately — see this table's own migration comment for the precedent
 * (M1's OutboxEvent shipped the same way, a minimal slice ahead of the full
 * M-06). Extend into the full audit_log/platform_access_log set when M-13
 * itself is built.
 */
@Entity
@Table(name = "sensitive_access_log")
public class SensitiveAccessLog {

    @Id
    private UUID id;

    @Column(name = "org_id", nullable = false)
    private UUID orgId;

    @Column(name = "actor_user_id", nullable = false)
    private UUID actorUserId;

    @Column(name = "entity_type", nullable = false, length = 60)
    private String entityType;

    @Column(name = "entity_id", nullable = false)
    private UUID entityId;

    @Column(nullable = false, length = 60)
    private String field;

    @Column(columnDefinition = "text")
    private String reason;

    @CreationTimestamp
    @Column(name = "occurred_at", nullable = false, updatable = false)
    private Instant occurredAt;

    protected SensitiveAccessLog() {
    }

    public SensitiveAccessLog(UUID id, UUID orgId, UUID actorUserId, String entityType, UUID entityId, String field, String reason) {
        this.id = id;
        this.orgId = orgId;
        this.actorUserId = actorUserId;
        this.entityType = entityType;
        this.entityId = entityId;
        this.field = field;
        this.reason = reason;
    }

    public UUID getId() {
        return id;
    }
}
