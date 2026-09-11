package com.shardeya.foundation.notification;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.UUID;

/**
 * M-06 Notification Engine, in-app slice only (01-DATA-MODEL.md §8). M3
 * scope per 05-MILESTONES.md: "in-app bell only -- WhatsApp/SMS in M7."
 * title_key/body_key are i18n keys, never rendered strings (CLAUDE.md rule
 * #14), so the bell always renders in the viewer's CURRENT language rather
 * than whatever language was active when the event fired.
 */
@Entity
@Table(name = "notification")
public class Notification {

    @Id
    private UUID id;

    @Column(name = "org_id", nullable = false)
    private UUID orgId;

    @Column(name = "recipient_user_id", nullable = false)
    private UUID recipientUserId;

    @Column(name = "type_code", nullable = false, length = 60)
    private String typeCode;

    @Column(name = "title_key", nullable = false, length = 120)
    private String titleKey;

    @Column(name = "body_key", length = 120)
    private String bodyKey;

    @Column(nullable = false, columnDefinition = "jsonb")
    @JdbcTypeCode(SqlTypes.JSON)
    private String params;

    @Column(name = "entity_type", length = 60)
    private String entityType;

    @Column(name = "entity_id")
    private UUID entityId;

    @Column(nullable = false)
    private short priority;

    @Column(name = "read_at")
    private Instant readAt;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected Notification() {
    }

    public Notification(UUID id, UUID orgId, UUID recipientUserId, String typeCode, String titleKey, String bodyKey,
                         String paramsJson, String entityType, UUID entityId) {
        this.id = id;
        this.orgId = orgId;
        this.recipientUserId = recipientUserId;
        this.typeCode = typeCode;
        this.titleKey = titleKey;
        this.bodyKey = bodyKey;
        this.params = paramsJson;
        this.entityType = entityType;
        this.entityId = entityId;
    }

    public UUID getId() {
        return id;
    }

    public UUID getRecipientUserId() {
        return recipientUserId;
    }

    public String getTypeCode() {
        return typeCode;
    }

    public String getTitleKey() {
        return titleKey;
    }

    public String getBodyKey() {
        return bodyKey;
    }

    public String getParams() {
        return params;
    }

    public String getEntityType() {
        return entityType;
    }

    public UUID getEntityId() {
        return entityId;
    }

    public Instant getReadAt() {
        return readAt;
    }

    public void setReadAt(Instant readAt) {
        this.readAt = readAt;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
