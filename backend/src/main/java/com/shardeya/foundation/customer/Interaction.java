package com.shardeya.foundation.customer;

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

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/**
 * M-12 §3 -- the universal append-only follow-up log (§6.5, §13.3). Deletion
 * is blocked outright by a DB rule (V4_004); amendment (not deletion) is how
 * a mistake gets fixed, gated to a 15-minute grace window by
 * {@link InteractionService}, never here.
 */
@Entity
@Table(name = "interaction")
public class Interaction {

    public enum Type { CALL, VISIT, WHATSAPP, MEETING, EMAIL, SMS, NOTE }

    public enum Result { POSITIVE, NEUTRAL, NEGATIVE, NOT_INTERESTED, NEXT_SCHEDULED, NO_FURTHER }

    @Id
    private UUID id;

    @Column(name = "org_id", nullable = false)
    private UUID orgId;

    @Column(name = "customer_id", nullable = false)
    private UUID customerId;

    @Column(name = "property_id")
    private UUID propertyId;

    @Column(name = "project_id")
    private UUID projectId;

    @Column(name = "plot_id")
    private UUID plotId;

    @Column(name = "deal_id")
    private UUID dealId;

    @Column(name = "occurred_on", nullable = false)
    private LocalDate occurredOn;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.NAMED_ENUM)
    @Column(nullable = false)
    private Type type;

    @Column(nullable = false, columnDefinition = "text")
    private String remarks;

    @Column(name = "next_follow_up_date")
    private LocalDate nextFollowUpDate;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.NAMED_ENUM)
    private Result result;

    @Column(name = "conducted_by")
    private UUID conductedBy;

    @Column(name = "amended_at")
    private Instant amendedAt;

    @Column(name = "amended_by")
    private UUID amendedBy;

    @Column(name = "original_remarks", columnDefinition = "text")
    private String originalRemarks;

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

    protected Interaction() {
    }

    public Interaction(UUID id, UUID orgId, UUID customerId, LocalDate occurredOn, Type type, String remarks,
                        UUID conductedBy) {
        this.id = id;
        this.orgId = orgId;
        this.customerId = customerId;
        this.occurredOn = occurredOn;
        this.type = type;
        this.remarks = remarks;
        this.conductedBy = conductedBy;
    }

    public UUID getId() {
        return id;
    }

    public UUID getOrgId() {
        return orgId;
    }

    public UUID getCustomerId() {
        return customerId;
    }

    public UUID getPropertyId() {
        return propertyId;
    }

    public void setPropertyId(UUID propertyId) {
        this.propertyId = propertyId;
    }

    public UUID getProjectId() {
        return projectId;
    }

    public void setProjectId(UUID projectId) {
        this.projectId = projectId;
    }

    public UUID getPlotId() {
        return plotId;
    }

    public void setPlotId(UUID plotId) {
        this.plotId = plotId;
    }

    public UUID getDealId() {
        return dealId;
    }

    public LocalDate getOccurredOn() {
        return occurredOn;
    }

    public Type getType() {
        return type;
    }

    public String getRemarks() {
        return remarks;
    }

    public void setRemarks(String remarks) {
        this.remarks = remarks;
    }

    public LocalDate getNextFollowUpDate() {
        return nextFollowUpDate;
    }

    public void setNextFollowUpDate(LocalDate nextFollowUpDate) {
        this.nextFollowUpDate = nextFollowUpDate;
    }

    public Result getResult() {
        return result;
    }

    public void setResult(Result result) {
        this.result = result;
    }

    public UUID getConductedBy() {
        return conductedBy;
    }

    public Instant getAmendedAt() {
        return amendedAt;
    }

    public void setAmendedAt(Instant amendedAt) {
        this.amendedAt = amendedAt;
    }

    public UUID getAmendedBy() {
        return amendedBy;
    }

    public void setAmendedBy(UUID amendedBy) {
        this.amendedBy = amendedBy;
    }

    public String getOriginalRemarks() {
        return originalRemarks;
    }

    public void setOriginalRemarks(String originalRemarks) {
        this.originalRemarks = originalRemarks;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public UUID getCreatedBy() {
        return createdBy;
    }

    public void setCreatedBy(UUID createdBy) {
        this.createdBy = createdBy;
    }
}
