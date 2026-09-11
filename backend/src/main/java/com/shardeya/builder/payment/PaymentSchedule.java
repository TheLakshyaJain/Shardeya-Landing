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

@Entity
@Table(name = "payment_schedule")
public class PaymentSchedule {

    public enum Status { PENDING, PARTIALLY_PAID, PAID, OVERDUE, WAIVED }

    @Id
    private UUID id;

    @Column(name = "org_id", nullable = false)
    private UUID orgId;

    @Column(name = "plot_sale_id", nullable = false)
    private UUID plotSaleId;

    @Column(name = "sequence_no", nullable = false)
    private short sequenceNo;

    @Column(length = 80)
    private String label;

    @Column(name = "expected_amount", nullable = false, precision = 19, scale = 2)
    private BigDecimal expectedAmount;

    @Column(name = "due_date", nullable = false)
    private LocalDate dueDate;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.NAMED_ENUM)
    @Column(nullable = false)
    private Status status = Status.PENDING;

    // Trigger-maintained (V3_010) from payment_allocation -- never written
    // directly by application code, same as plot_sale.total_paid.
    @Column(name = "amount_allocated", insertable = false, updatable = false, precision = 19, scale = 2)
    private BigDecimal amountAllocated;

    @Column(name = "reminder_enabled", nullable = false)
    private boolean reminderEnabled = true;

    @Column(name = "last_reminder_sent_at")
    private Instant lastReminderSentAt;

    @Column(name = "waive_reason", columnDefinition = "text")
    private String waiveReason;

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

    @Column(name = "deleted_at")
    private Instant deletedAt;

    @Column(name = "deleted_by")
    private UUID deletedBy;

    @Version
    @Column(nullable = false)
    private long version;

    protected PaymentSchedule() {
    }

    public PaymentSchedule(UUID id, UUID orgId, UUID plotSaleId, int sequenceNo, String label,
                            BigDecimal expectedAmount, LocalDate dueDate) {
        this.id = id;
        this.orgId = orgId;
        this.plotSaleId = plotSaleId;
        this.sequenceNo = (short) sequenceNo;
        this.label = label;
        this.expectedAmount = expectedAmount;
        this.dueDate = dueDate;
    }

    public UUID getId() {
        return id;
    }

    public UUID getPlotSaleId() {
        return plotSaleId;
    }

    public short getSequenceNo() {
        return sequenceNo;
    }

    public String getLabel() {
        return label;
    }

    public void setLabel(String label) {
        this.label = label;
    }

    public BigDecimal getExpectedAmount() {
        return expectedAmount;
    }

    public void setExpectedAmount(BigDecimal expectedAmount) {
        this.expectedAmount = expectedAmount;
    }

    public LocalDate getDueDate() {
        return dueDate;
    }

    public void setDueDate(LocalDate dueDate) {
        this.dueDate = dueDate;
    }

    public Status getStatus() {
        return status;
    }

    public void setStatus(Status status) {
        this.status = status;
    }

    public BigDecimal getAmountAllocated() {
        return amountAllocated;
    }

    public boolean isReminderEnabled() {
        return reminderEnabled;
    }

    public void setReminderEnabled(boolean reminderEnabled) {
        this.reminderEnabled = reminderEnabled;
    }

    public Instant getLastReminderSentAt() {
        return lastReminderSentAt;
    }

    public void setLastReminderSentAt(Instant lastReminderSentAt) {
        this.lastReminderSentAt = lastReminderSentAt;
    }

    public String getWaiveReason() {
        return waiveReason;
    }

    public void setWaiveReason(String waiveReason) {
        this.waiveReason = waiveReason;
    }

    public Instant getDeletedAt() {
        return deletedAt;
    }

    public void setDeletedAt(Instant deletedAt) {
        this.deletedAt = deletedAt;
    }

    public long getVersion() {
        return version;
    }
}
