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

/** B-14 §20.3 (01-DATA-MODEL.md §5) -- the resolution algorithm's own source data (BrokerCommissionConfigService). */
@Entity
@Table(name = "broker_commission_config")
public class BrokerCommissionConfig {

    public enum Scope { GLOBAL, PROJECT, PLOT }

    @Id
    private UUID id;

    @Column(name = "org_id", nullable = false)
    private UUID orgId;

    @Column(name = "broker_partner_id", nullable = false)
    private UUID brokerPartnerId;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.NAMED_ENUM)
    @Column(nullable = false)
    private Scope scope;

    @Column(name = "project_id")
    private UUID projectId;

    @Column(name = "plot_id")
    private UUID plotId;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.NAMED_ENUM)
    @Column(name = "commission_type", nullable = false)
    private BrokerPartner.CommissionType commissionType;

    @Column(name = "rate_value", nullable = false, precision = 19, scale = 3)
    private BigDecimal rateValue;

    @Column(name = "effective_from", nullable = false)
    private LocalDate effectiveFrom;

    @Column(name = "effective_to")
    private LocalDate effectiveTo;

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

    protected BrokerCommissionConfig() {
    }

    public BrokerCommissionConfig(UUID id, UUID orgId, UUID brokerPartnerId, Scope scope, UUID projectId, UUID plotId,
                                   BrokerPartner.CommissionType commissionType, BigDecimal rateValue, LocalDate effectiveFrom) {
        this.id = id;
        this.orgId = orgId;
        this.brokerPartnerId = brokerPartnerId;
        this.scope = scope;
        this.projectId = projectId;
        this.plotId = plotId;
        this.commissionType = commissionType;
        this.rateValue = rateValue;
        this.effectiveFrom = effectiveFrom;
    }

    public UUID getId() {
        return id;
    }

    public UUID getOrgId() {
        return orgId;
    }

    public UUID getBrokerPartnerId() {
        return brokerPartnerId;
    }

    public Scope getScope() {
        return scope;
    }

    public UUID getProjectId() {
        return projectId;
    }

    public UUID getPlotId() {
        return plotId;
    }

    public BrokerPartner.CommissionType getCommissionType() {
        return commissionType;
    }

    public void setCommissionType(BrokerPartner.CommissionType commissionType) {
        this.commissionType = commissionType;
    }

    public BigDecimal getRateValue() {
        return rateValue;
    }

    public void setRateValue(BigDecimal rateValue) {
        this.rateValue = rateValue;
    }

    public LocalDate getEffectiveFrom() {
        return effectiveFrom;
    }

    public void setEffectiveFrom(LocalDate effectiveFrom) {
        this.effectiveFrom = effectiveFrom;
    }

    public LocalDate getEffectiveTo() {
        return effectiveTo;
    }

    public void setEffectiveTo(LocalDate effectiveTo) {
        this.effectiveTo = effectiveTo;
    }

    public Instant getDeletedAt() {
        return deletedAt;
    }

    public void setDeletedAt(Instant deletedAt) {
        this.deletedAt = deletedAt;
    }

    public void setDeletedBy(UUID deletedBy) {
        this.deletedBy = deletedBy;
    }

    public void setCreatedBy(UUID createdBy) {
        this.createdBy = createdBy;
    }

    public void setUpdatedBy(UUID updatedBy) {
        this.updatedBy = updatedBy;
    }
}
