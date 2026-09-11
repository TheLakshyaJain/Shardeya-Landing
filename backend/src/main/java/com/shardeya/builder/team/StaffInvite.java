package com.shardeya.builder.team;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.Instant;
import java.util.UUID;

/** B-12 §3 -- tracks the single-use set-password link's lifecycle for display (Invited/expiry/resend count); the actual token lookup goes through Redis, same as password-reset (see TeamService). */
@Entity
@Table(name = "staff_invite")
public class StaffInvite {

    @Id
    private UUID id;

    @Column(name = "org_id", nullable = false)
    private UUID orgId;

    @Column(name = "app_user_id", nullable = false)
    private UUID appUserId;

    @Column(name = "token_hash", nullable = false)
    private String tokenHash;

    @Column(nullable = false, length = 10)
    private String channel;

    @Column(name = "sent_at")
    private Instant sentAt;

    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;

    @Column(name = "accepted_at")
    private Instant acceptedAt;

    @Column(name = "resend_count", nullable = false)
    private short resendCount;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "created_by")
    private UUID createdBy;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Column(name = "deleted_at")
    private Instant deletedAt;

    @Version
    @Column(nullable = false)
    private long version;

    protected StaffInvite() {
    }

    public StaffInvite(UUID id, UUID orgId, UUID appUserId, String tokenHash, String channel, Instant expiresAt) {
        this.id = id;
        this.orgId = orgId;
        this.appUserId = appUserId;
        this.tokenHash = tokenHash;
        this.channel = channel;
        this.expiresAt = expiresAt;
    }

    public UUID getId() {
        return id;
    }

    public UUID getOrgId() {
        return orgId;
    }

    public UUID getAppUserId() {
        return appUserId;
    }

    public String getTokenHash() {
        return tokenHash;
    }

    public void setTokenHash(String tokenHash) {
        this.tokenHash = tokenHash;
    }

    public String getChannel() {
        return channel;
    }

    public Instant getSentAt() {
        return sentAt;
    }

    public void setSentAt(Instant sentAt) {
        this.sentAt = sentAt;
    }

    public Instant getExpiresAt() {
        return expiresAt;
    }

    public void setExpiresAt(Instant expiresAt) {
        this.expiresAt = expiresAt;
    }

    public Instant getAcceptedAt() {
        return acceptedAt;
    }

    public void setAcceptedAt(Instant acceptedAt) {
        this.acceptedAt = acceptedAt;
    }

    public short getResendCount() {
        return resendCount;
    }

    public void setResendCount(short resendCount) {
        this.resendCount = resendCount;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getDeletedAt() {
        return deletedAt;
    }

    public void setDeletedAt(Instant deletedAt) {
        this.deletedAt = deletedAt;
    }
}
