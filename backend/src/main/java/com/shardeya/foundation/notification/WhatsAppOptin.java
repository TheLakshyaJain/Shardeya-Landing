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
 * 01-DATA-MODEL.md §8 whatsapp_optin. One row per (org, mobile) tracking
 * CURRENT consent state -- a "STOP" reply sets opted_out_at on the same
 * row rather than inserting a new one; a later re-opt-in clears it back to
 * null. source distinguishes a staff member opting their own mobile in via
 * /settings ("SELF_SERVICE") from consent the builder captured on a
 * buyer's behalf ("BUILDER_CAPTURED", §22.4 -- buyers are third parties).
 * {@code capturedBy} is the visible-in-data half of that same distinction:
 * only ever set for a BUILDER_CAPTURED row (the staff/admin user who
 * ticked the consent checkbox on the buyer's behalf); a SELF_SERVICE row
 * has no "captured by" concept at all -- the person themselves proved,
 * via OTP, that they can receive a code, so there is no third party to
 * attribute consent to.
 */
@Entity
@Table(name = "whatsapp_optin")
public class WhatsAppOptin {

    @Id
    private UUID id;

    @Column(name = "org_id", nullable = false)
    private UUID orgId;

    @Column(nullable = false, length = 15)
    private String mobile;

    @Column(name = "opted_in_at", nullable = false)
    private Instant optedInAt;

    @Column(name = "opted_out_at")
    private Instant optedOutAt;

    @Column(nullable = false, length = 40)
    private String source;

    @Column(name = "captured_by")
    private UUID capturedBy;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected WhatsAppOptin() {
    }

    public WhatsAppOptin(UUID id, UUID orgId, String mobile, String source) {
        this.id = id;
        this.orgId = orgId;
        this.mobile = mobile;
        this.optedInAt = Instant.now();
        this.source = source;
    }

    public WhatsAppOptin(UUID id, UUID orgId, String mobile, String source, UUID capturedBy) {
        this(id, orgId, mobile, source);
        this.capturedBy = capturedBy;
    }

    public UUID getId() {
        return id;
    }

    public String getMobile() {
        return mobile;
    }

    public String getSource() {
        return source;
    }

    public UUID getCapturedBy() {
        return capturedBy;
    }

    public Instant getOptedOutAt() {
        return optedOutAt;
    }

    public boolean isActive() {
        return optedOutAt == null;
    }

    public void reOptIn() {
        this.optedOutAt = null;
        this.optedInAt = Instant.now();
    }

    public void optOut() {
        this.optedOutAt = Instant.now();
    }

    /** Records who (re-)captured this consent -- only meaningful for BUILDER_CAPTURED rows. */
    public void recordCapturedBy(UUID capturedBy) {
        this.capturedBy = capturedBy;
    }
}
