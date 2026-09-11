package com.shardeya.builder.broker;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * 06-BROKER-NETWORK-ENGINE.md §11, build-order step 7 -- an append-only
 * audit trail of every designation change (AUTOMATIC here; MANUAL is
 * step 8, not built yet). Immutable: no deleted_at/updated_at, DELETE
 * blocked outright at the DB (V65_009's RULE), same shape as
 * CommissionRelease.
 */
@Entity
@Table(name = "designation_history")
public class DesignationHistory {

    // MANUAL is step 8 (not built yet). CANCELLATION_REVERSAL (step 9) is
    // distinct from AUTOMATIC precisely because it can move a designation
    // DOWN -- AUTOMATIC (step 7's completion-driven promotion) never can.
    public enum ChangeType { AUTOMATIC, MANUAL, CANCELLATION_REVERSAL }

    @Id
    private UUID id;

    @Column(name = "org_id", nullable = false)
    private UUID orgId;

    @Column(name = "broker_id", nullable = false)
    private UUID brokerId;

    @Column(name = "previous_designation_id")
    private UUID previousDesignationId;

    @Column(name = "new_designation_id", nullable = false)
    private UUID newDesignationId;

    @Column(name = "previous_rate", precision = 19, scale = 2)
    private BigDecimal previousRate;

    @Column(name = "new_rate", nullable = false, precision = 19, scale = 2)
    private BigDecimal newRate;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.NAMED_ENUM)
    @Column(name = "change_type", nullable = false)
    private ChangeType changeType;

    @Column(columnDefinition = "text")
    private String reason;

    @Column(name = "effective_at", nullable = false)
    private Instant effectiveAt = Instant.now();

    @Column(name = "changed_by")
    private UUID changedBy;

    protected DesignationHistory() {
    }

    public DesignationHistory(UUID id, UUID orgId, UUID brokerId, UUID previousDesignationId, UUID newDesignationId,
                               BigDecimal previousRate, BigDecimal newRate, ChangeType changeType, String reason) {
        this.id = id;
        this.orgId = orgId;
        this.brokerId = brokerId;
        this.previousDesignationId = previousDesignationId;
        this.newDesignationId = newDesignationId;
        this.previousRate = previousRate;
        this.newRate = newRate;
        this.changeType = changeType;
        this.reason = reason;
    }

    public UUID getId() {
        return id;
    }

    public UUID getOrgId() {
        return orgId;
    }

    public UUID getBrokerId() {
        return brokerId;
    }

    public UUID getPreviousDesignationId() {
        return previousDesignationId;
    }

    public UUID getNewDesignationId() {
        return newDesignationId;
    }

    public BigDecimal getPreviousRate() {
        return previousRate;
    }

    public BigDecimal getNewRate() {
        return newRate;
    }

    public ChangeType getChangeType() {
        return changeType;
    }

    public String getReason() {
        return reason;
    }

    public Instant getEffectiveAt() {
        return effectiveAt;
    }

    public UUID getChangedBy() {
        return changedBy;
    }

    public void setChangedBy(UUID changedBy) {
        this.changedBy = changedBy;
    }
}
