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

/** B-14 §20.5 -- immutable (V6_008's RULE blocks DELETE outright); corrections are reversals (negative rows via reversesPaymentId), same shape as builder.payment.PaymentRecord. Deliberately NO deleted_at, matching that table's own reasoning. */
@Entity
@Table(name = "commission_payment")
public class CommissionPayment {

    public enum Mode { CASH, CHEQUE, BANK_TRANSFER, UPI, DD }

    @Id
    private UUID id;

    @Column(name = "org_id", nullable = false)
    private UUID orgId;

    @Column(name = "commission_ledger_entry_id", nullable = false)
    private UUID commissionLedgerEntryId;

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

    @Column(name = "paid_by")
    private UUID paidBy;

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

    protected CommissionPayment() {
    }

    public CommissionPayment(UUID id, UUID orgId, UUID commissionLedgerEntryId, BigDecimal amount, LocalDate paidOn,
                              Mode mode, String reference, UUID paidBy, String remarks, UUID reversesPaymentId) {
        this.id = id;
        this.orgId = orgId;
        this.commissionLedgerEntryId = commissionLedgerEntryId;
        this.amount = amount;
        this.paidOn = paidOn;
        this.mode = mode;
        this.reference = reference;
        this.paidBy = paidBy;
        this.remarks = remarks;
        this.reversesPaymentId = reversesPaymentId;
    }

    public UUID getId() {
        return id;
    }

    public UUID getOrgId() {
        return orgId;
    }

    public UUID getCommissionLedgerEntryId() {
        return commissionLedgerEntryId;
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

    public UUID getPaidBy() {
        return paidBy;
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
