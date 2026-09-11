package com.shardeya.foundation.notification;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.UUID;

/**
 * 01-DATA-MODEL.md §8 message_delivery -- audit/billing record for every
 * real (non-in-app) send attempt, one row per channel dispatch. Recorded
 * by OutboxPoller right after calling the relevant gateway, for both
 * success (status SENT, provider_message_id if the provider gave one back)
 * and failure (status FAILED, error_code set). notification_id is
 * deliberately nullable: buyer-facing sends and the OTP/staff-invite SMS
 * paths have no in-app notification row to point back at at all.
 */
@Entity
@Table(name = "message_delivery")
public class MessageDelivery {

    public enum Channel { WHATSAPP, SMS, EMAIL }

    public enum Status { QUEUED, SENT, DELIVERED, READ, FAILED }

    @Id
    private UUID id;

    @Column(name = "org_id", nullable = false)
    private UUID orgId;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.NAMED_ENUM)
    @Column(nullable = false)
    private Channel channel;

    @Column(name = "recipient_masked", nullable = false, length = 120)
    private String recipientMasked;

    @Column(name = "template_code", length = 60)
    private String templateCode;

    @Column(nullable = false, length = 40)
    private String provider;

    @Column(name = "provider_message_id", length = 120)
    private String providerMessageId;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.NAMED_ENUM)
    @Column(nullable = false)
    private Status status = Status.QUEUED;

    @Column(name = "error_code", length = 120)
    private String errorCode;

    @Column(name = "cost_paise")
    private Integer costPaise;

    @Column(name = "sent_at")
    private Instant sentAt;

    @Column(name = "delivered_at")
    private Instant deliveredAt;

    @Column(name = "notification_id")
    private UUID notificationId;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected MessageDelivery() {
    }

    public MessageDelivery(UUID id, UUID orgId, Channel channel, String recipientMasked, String templateCode,
                            String provider, UUID notificationId) {
        this.id = id;
        this.orgId = orgId;
        this.channel = channel;
        this.recipientMasked = recipientMasked;
        this.templateCode = templateCode;
        this.provider = provider;
        this.notificationId = notificationId;
    }

    public UUID getId() {
        return id;
    }

    public Channel getChannel() {
        return channel;
    }

    public String getRecipientMasked() {
        return recipientMasked;
    }

    public String getTemplateCode() {
        return templateCode;
    }

    public String getProvider() {
        return provider;
    }

    public String getProviderMessageId() {
        return providerMessageId;
    }

    public Status getStatus() {
        return status;
    }

    public String getErrorCode() {
        return errorCode;
    }

    public Instant getSentAt() {
        return sentAt;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void markSent(String providerMessageId) {
        this.status = Status.SENT;
        this.providerMessageId = providerMessageId;
        this.sentAt = Instant.now();
    }

    public void markFailed(String errorCode) {
        this.status = Status.FAILED;
        this.errorCode = errorCode;
    }
}
