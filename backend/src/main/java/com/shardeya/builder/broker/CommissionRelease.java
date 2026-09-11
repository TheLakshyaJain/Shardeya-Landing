package com.shardeya.builder.broker;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import org.hibernate.annotations.CreationTimestamp;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/**
 * 06-BROKER-NETWORK-ENGINE.md §8/§11 -- immutable (V65_007's RULE blocks
 * DELETE outright); {@code amount} is a delta against the authoritative
 * cumulative "released so far" figure, not a flat per-payment fraction --
 * see the migration's own comment for why. Deliberately NO deleted_at/
 * updated_at, same reasoning as CommissionPayment.
 */
@Entity
@Table(name = "commission_release")
public class CommissionRelease {

    @Id
    private UUID id;

    @Column(name = "org_id", nullable = false)
    private UUID orgId;

    @Column(name = "booking_commission_id", nullable = false)
    private UUID bookingCommissionId;

    @Column(name = "payment_record_id", nullable = false)
    private UUID paymentRecordId;

    @Column(nullable = false, precision = 19, scale = 2)
    private BigDecimal amount;

    @Column(name = "released_on", nullable = false)
    private LocalDate releasedOn;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "created_by")
    private UUID createdBy;

    @Version
    @Column(nullable = false)
    private long version;

    protected CommissionRelease() {
    }

    public CommissionRelease(UUID id, UUID orgId, UUID bookingCommissionId, UUID paymentRecordId, BigDecimal amount,
                              LocalDate releasedOn) {
        this.id = id;
        this.orgId = orgId;
        this.bookingCommissionId = bookingCommissionId;
        this.paymentRecordId = paymentRecordId;
        this.amount = amount;
        this.releasedOn = releasedOn;
    }

    public UUID getId() {
        return id;
    }

    public UUID getOrgId() {
        return orgId;
    }

    public UUID getBookingCommissionId() {
        return bookingCommissionId;
    }

    public UUID getPaymentRecordId() {
        return paymentRecordId;
    }

    public BigDecimal getAmount() {
        return amount;
    }

    public LocalDate getReleasedOn() {
        return releasedOn;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedBy(UUID createdBy) {
        this.createdBy = createdBy;
    }
}
