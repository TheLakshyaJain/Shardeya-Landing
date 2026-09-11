package com.shardeya.builder.payment;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.hibernate.annotations.CreationTimestamp;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "payment_allocation")
public class PaymentAllocation {

    @Id
    private UUID id;

    @Column(name = "org_id", nullable = false)
    private UUID orgId;

    @Column(name = "payment_record_id", nullable = false)
    private UUID paymentRecordId;

    @Column(name = "payment_schedule_id", nullable = false)
    private UUID paymentScheduleId;

    @Column(nullable = false, precision = 19, scale = 2)
    private BigDecimal amount;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "created_by")
    private UUID createdBy;

    @Column(name = "deleted_at")
    private Instant deletedAt;

    @Column(name = "deleted_by")
    private UUID deletedBy;

    protected PaymentAllocation() {
    }

    public PaymentAllocation(UUID id, UUID orgId, UUID paymentRecordId, UUID paymentScheduleId, BigDecimal amount) {
        this.id = id;
        this.orgId = orgId;
        this.paymentRecordId = paymentRecordId;
        this.paymentScheduleId = paymentScheduleId;
        this.amount = amount;
    }

    public UUID getId() {
        return id;
    }

    public UUID getPaymentRecordId() {
        return paymentRecordId;
    }

    public UUID getPaymentScheduleId() {
        return paymentScheduleId;
    }

    public BigDecimal getAmount() {
        return amount;
    }

    public Instant getDeletedAt() {
        return deletedAt;
    }

    public void setDeletedAt(Instant deletedAt) {
        this.deletedAt = deletedAt;
    }
}
