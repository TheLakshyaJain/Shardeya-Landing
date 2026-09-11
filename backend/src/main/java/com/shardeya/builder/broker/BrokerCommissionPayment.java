package com.shardeya.builder.broker;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.annotations.UpdateTimestamp;
import org.hibernate.type.SqlTypes;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/**
 * 06-BROKER-NETWORK-ENGINE.md §8a -- the builder actually paying a
 * DESIGNATION broker, mirroring {@link CommissionPayment} (M6's identical
 * concept for PERCENTAGE/FIXED brokers) for the row shape itself: same
 * immutable/reversal discipline, same reuse of the global {@code
 * payment_mode} Postgres enum. Deliberately NOT 1:1 with a single
 * commission entity the way {@code CommissionPayment} is 1:1 with a
 * {@code commission_ledger_entry} -- a DESIGNATION broker's payout spreads
 * oldest-first across potentially many {@link BookingCommission} rows, so
 * this table has no direct FK to one; {@link BrokerCommissionPaymentAllocation}
 * links a payout to the specific entries it actually settles.
 *
 * <p>Immutable (V65_015's RULE blocks DELETE outright); corrections are
 * reversals (negative-amount rows via {@code reversesPaymentId}), never
 * edits/deletes -- same shape as {@code PaymentRecord}/{@code CommissionPayment}
 * before it.
 */
@Entity
@Table(name = "broker_commission_payment")
public class BrokerCommissionPayment {

    public enum Mode { CASH, CHEQUE, BANK_TRANSFER, UPI, DD }

    @Id
    private UUID id;

    @Column(name = "org_id", nullable = false)
    private UUID orgId;

    @Column(name = "beneficiary_broker_id", nullable = false)
    private UUID beneficiaryBrokerId;

    @Column(nullable = false, precision = 19, scale = 2)
    private BigDecimal amount;

    @Column(name = "paid_on", nullable = false)
    private LocalDate paidOn;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.NAMED_ENUM)
    @Column(nullable = false)
    private Mode mode;

    @Column(length = 120)
    private String reference;

    @Column(columnDefinition = "text")
    private String remarks;

    @Column(name = "reverses_payment_id")
    private UUID reversesPaymentId;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "created_by")
    private UUID createdBy;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Column(name = "updated_by")
    private UUID updatedBy;

    @Version
    @Column(nullable = false)
    private long version;

    protected BrokerCommissionPayment() {
    }

    public BrokerCommissionPayment(UUID id, UUID orgId, UUID beneficiaryBrokerId, BigDecimal amount, LocalDate paidOn,
                                    Mode mode, String reference, String remarks, UUID reversesPaymentId) {
        this.id = id;
        this.orgId = orgId;
        this.beneficiaryBrokerId = beneficiaryBrokerId;
        this.amount = amount;
        this.paidOn = paidOn;
        this.mode = mode;
        this.reference = reference;
        this.remarks = remarks;
        this.reversesPaymentId = reversesPaymentId;
    }

    public UUID getId() {
        return id;
    }

    public UUID getOrgId() {
        return orgId;
    }

    public UUID getBeneficiaryBrokerId() {
        return beneficiaryBrokerId;
    }

    public BigDecimal getAmount() {
        return amount;
    }

    public LocalDate getPaidOn() {
        return paidOn;
    }

    public Mode getMode() {
        return mode;
    }

    public String getReference() {
        return reference;
    }

    public String getRemarks() {
        return remarks;
    }

    public UUID getReversesPaymentId() {
        return reversesPaymentId;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedBy(UUID createdBy) {
        this.createdBy = createdBy;
    }

    public void setUpdatedBy(UUID updatedBy) {
        this.updatedBy = updatedBy;
    }
}
