package com.shardeya.builder.payment;

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
 * Immutable -- CLAUDE.md rule #7. No {@code deletedAt}/soft-delete: the DB
 * itself blocks DELETE outright (V3_003's {@code no_delete_payment_record}
 * RULE). The only correction path is a new negative-amount row referencing
 * {@code reversesPaymentId}.
 */
@Entity
@Table(name = "payment_record")
public class PaymentRecord {

    public enum Mode { CASH, CHEQUE, BANK_TRANSFER, UPI, DD }

    public enum ChequeStatus { PENDING, CLEARED, BOUNCED }

    @Id
    private UUID id;

    @Column(name = "org_id", nullable = false)
    private UUID orgId;

    @Column(name = "plot_sale_id", nullable = false)
    private UUID plotSaleId;

    @Column(name = "project_id", nullable = false)
    private UUID projectId;

    @Column(name = "plot_id", nullable = false)
    private UUID plotId;

    @Column(name = "receipt_no", nullable = false, length = 30)
    private String receiptNo;

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

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.NAMED_ENUM)
    @Column(name = "cheque_status")
    private ChequeStatus chequeStatus;

    @Column(name = "received_by", nullable = false)
    private UUID receivedBy;

    @Column(columnDefinition = "text")
    private String remarks;

    @Column(name = "reverses_payment_id")
    private UUID reversesPaymentId;

    @Column(name = "receipt_document_id")
    private UUID receiptDocumentId;

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

    protected PaymentRecord() {
    }

    public PaymentRecord(UUID id, UUID orgId, UUID plotSaleId, UUID projectId, UUID plotId, String receiptNo,
                         BigDecimal amount, LocalDate paidOn, Mode mode, String reference, UUID receivedBy) {
        this.id = id;
        this.orgId = orgId;
        this.plotSaleId = plotSaleId;
        this.projectId = projectId;
        this.plotId = plotId;
        this.receiptNo = receiptNo;
        this.amount = amount;
        this.paidOn = paidOn;
        this.mode = mode;
        this.reference = reference;
        this.receivedBy = receivedBy;
        if (mode == Mode.CHEQUE) {
            this.chequeStatus = ChequeStatus.PENDING;
        }
    }

    public UUID getId() {
        return id;
    }

    public UUID getOrgId() {
        return orgId;
    }

    public UUID getPlotSaleId() {
        return plotSaleId;
    }

    public UUID getProjectId() {
        return projectId;
    }

    public UUID getPlotId() {
        return plotId;
    }

    public String getReceiptNo() {
        return receiptNo;
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

    public ChequeStatus getChequeStatus() {
        return chequeStatus;
    }

    public void setChequeStatus(ChequeStatus chequeStatus) {
        this.chequeStatus = chequeStatus;
    }

    public UUID getReceivedBy() {
        return receivedBy;
    }

    public String getRemarks() {
        return remarks;
    }

    public void setRemarks(String remarks) {
        this.remarks = remarks;
    }

    public UUID getReversesPaymentId() {
        return reversesPaymentId;
    }

    public void setReversesPaymentId(UUID reversesPaymentId) {
        this.reversesPaymentId = reversesPaymentId;
    }

    public UUID getReceiptDocumentId() {
        return receiptDocumentId;
    }

    public void setReceiptDocumentId(UUID receiptDocumentId) {
        this.receiptDocumentId = receiptDocumentId;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public long getVersion() {
        return version;
    }
}
