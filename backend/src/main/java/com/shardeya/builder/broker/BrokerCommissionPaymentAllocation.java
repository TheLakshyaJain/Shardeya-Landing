package com.shardeya.builder.broker;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import org.hibernate.annotations.CreationTimestamp;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * 06-BROKER-NETWORK-ENGINE.md §8a -- links one {@link BrokerCommissionPayment}
 * to the specific {@link BookingCommission} entries it settles, exactly the
 * role {@code PaymentAllocation} plays between {@code PaymentRecord} and
 * {@code PaymentSchedule}. Immutable (V65_016's RULE blocks DELETE
 * outright) -- mirrors {@link CommissionRelease}'s clean shape, not
 * {@code PaymentAllocation}'s (which carries an unused deleted_at/deleted_by
 * pair; nothing in this codebase's payout path ever soft-deletes an
 * allocation row).
 */
@Entity
@Table(name = "broker_commission_payment_allocation")
public class BrokerCommissionPaymentAllocation {

    @Id
    private UUID id;

    @Column(name = "org_id", nullable = false)
    private UUID orgId;

    @Column(name = "broker_commission_payment_id", nullable = false)
    private UUID brokerCommissionPaymentId;

    @Column(name = "booking_commission_id", nullable = false)
    private UUID bookingCommissionId;

    @Column(nullable = false, precision = 19, scale = 2)
    private BigDecimal amount;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "created_by")
    private UUID createdBy;

    @Version
    @Column(nullable = false)
    private long version;

    protected BrokerCommissionPaymentAllocation() {
    }

    public BrokerCommissionPaymentAllocation(UUID id, UUID orgId, UUID brokerCommissionPaymentId, UUID bookingCommissionId, BigDecimal amount) {
        this.id = id;
        this.orgId = orgId;
        this.brokerCommissionPaymentId = brokerCommissionPaymentId;
        this.bookingCommissionId = bookingCommissionId;
        this.amount = amount;
    }

    public UUID getId() {
        return id;
    }

    public UUID getOrgId() {
        return orgId;
    }

    public UUID getBrokerCommissionPaymentId() {
        return brokerCommissionPaymentId;
    }

    public UUID getBookingCommissionId() {
        return bookingCommissionId;
    }

    public BigDecimal getAmount() {
        return amount;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedBy(UUID createdBy) {
        this.createdBy = createdBy;
    }
}
