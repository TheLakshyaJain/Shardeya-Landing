package com.shardeya.foundation.notification;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.Instant;
import java.util.UUID;

/**
 * 01-DATA-MODEL.md §8 notification_preference. Sparse by design -- a row
 * only exists once a user overrides a type's defaults; see
 * NotificationDispatchService for how a missing row falls back to
 * NotificationType.defaultChannels/isMandatory.
 */
@Entity
@Table(name = "notification_preference")
public class NotificationPreference {

    @Id
    private UUID id;

    @Column(name = "org_id", nullable = false)
    private UUID orgId;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Column(name = "type_code", nullable = false, length = 60)
    private String typeCode;

    @Column(name = "in_app", nullable = false)
    private boolean inApp = true;

    @Column(nullable = false)
    private boolean whatsapp = true;

    @Column(nullable = false)
    private boolean sms = true;

    @Column(nullable = false)
    private boolean email = true;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected NotificationPreference() {
    }

    public NotificationPreference(UUID id, UUID orgId, UUID userId, String typeCode) {
        this.id = id;
        this.orgId = orgId;
        this.userId = userId;
        this.typeCode = typeCode;
    }

    public UUID getId() {
        return id;
    }

    public UUID getUserId() {
        return userId;
    }

    public String getTypeCode() {
        return typeCode;
    }

    public boolean isInApp() {
        return inApp;
    }

    public void setInApp(boolean inApp) {
        this.inApp = inApp;
    }

    public boolean isWhatsapp() {
        return whatsapp;
    }

    public void setWhatsapp(boolean whatsapp) {
        this.whatsapp = whatsapp;
    }

    public boolean isSms() {
        return sms;
    }

    public void setSms(boolean sms) {
        this.sms = sms;
    }

    public boolean isEmail() {
        return email;
    }

    public void setEmail(boolean email) {
        this.email = email;
    }
}
