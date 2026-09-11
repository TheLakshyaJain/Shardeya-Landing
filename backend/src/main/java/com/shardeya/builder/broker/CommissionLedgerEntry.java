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
 * B-14 §20.5 (01-DATA-MODEL.md §5). One row per sale actually attributed to
 * a real broker_partner_id -- created inside PlotSaleService's own sale
 * transaction (B-14's own §20 build-order step 5: "wire commission ledger
 * creation into PlotSaleService (M3)"), never on its own. config_snapshot
 * is the self-contained audit record proving exactly which rule produced
 * base_commission -- CLAUDE.md's "rate changes are never retroactive" rule
 * applied to commissions.
 */
@Entity
@Table(name = "commission_ledger_entry")
public class CommissionLedgerEntry {

    public enum Status { PENDING, PARTIALLY_PAID, PAID, CANCELLED }

    @Id
    private UUID id;

    @Column(name = "org_id", nullable = false)
    private UUID orgId;

    @Column(name = "broker_partner_id", nullable = false)
    private UUID brokerPartnerId;

    @Column(name = "plot_sale_id", nullable = false)
    private UUID plotSaleId;

    @Column(name = "project_id", nullable = false)
    private UUID projectId;

    @Column(name = "plot_id", nullable = false)
    private UUID plotId;

    @Column(name = "deal_date", nullable = false)
    private LocalDate dealDate;

    @Column(name = "deal_value", nullable = false, precision = 19, scale = 2)
    private BigDecimal dealValue;

    @Column(name = "base_commission", nullable = false, precision = 19, scale = 2)
    private BigDecimal baseCommission;

    @Column(name = "tier_bonus", nullable = false, precision = 19, scale = 2)
    private BigDecimal tierBonus = BigDecimal.ZERO;

    @Column(name = "total_commission", insertable = false, updatable = false, precision = 19, scale = 2)
    private BigDecimal totalCommission;

    @Column(name = "amount_paid", insertable = false, updatable = false, precision = 19, scale = 2)
    private BigDecimal amountPaid;

    @Column(name = "balance_due", insertable = false, updatable = false, precision = 19, scale = 2)
    private BigDecimal balanceDue;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.NAMED_ENUM)
    @Column(nullable = false)
    private Status status = Status.PENDING;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "config_snapshot", nullable = false)
    private String configSnapshot;

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

    protected CommissionLedgerEntry() {
    }

    public CommissionLedgerEntry(UUID id, UUID orgId, UUID brokerPartnerId, UUID plotSaleId, UUID projectId, UUID plotId,
                                  LocalDate dealDate, BigDecimal dealValue, BigDecimal baseCommission, BigDecimal tierBonus,
                                  String configSnapshot) {
        this.id = id;
        this.orgId = orgId;
        this.brokerPartnerId = brokerPartnerId;
        this.plotSaleId = plotSaleId;
        this.projectId = projectId;
        this.plotId = plotId;
        this.dealDate = dealDate;
        this.dealValue = dealValue;
        this.baseCommission = baseCommission;
        this.tierBonus = tierBonus;
        this.configSnapshot = configSnapshot;
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

    public UUID getPlotSaleId() {
        return plotSaleId;
    }

    public UUID getProjectId() {
        return projectId;
    }

    public UUID getPlotId() {
        return plotId;
    }

    public LocalDate getDealDate() {
        return dealDate;
    }

    public BigDecimal getDealValue() {
        return dealValue;
    }

    public BigDecimal getBaseCommission() {
        return baseCommission;
    }

    public BigDecimal getTierBonus() {
        return tierBonus;
    }

    public BigDecimal getTotalCommission() {
        return totalCommission;
    }

    public BigDecimal getAmountPaid() {
        return amountPaid;
    }

    public BigDecimal getBalanceDue() {
        return balanceDue;
    }

    public Status getStatus() {
        return status;
    }

    public void setStatus(Status status) {
        this.status = status;
    }

    public String getConfigSnapshot() {
        return configSnapshot;
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

    public Instant getDeletedAt() {
        return deletedAt;
    }

    public void setDeletedBy(UUID deletedBy) {
        this.deletedBy = deletedBy;
    }
}
