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
import java.util.UUID;

/**
 * 06-BROKER-NETWORK-ENGINE.md §7/§11 -- one row per beneficiary of a
 * DESIGNATION-broker booking's frozen commission tree (the selling broker
 * itself, plus every upline that has an UPLINE_DIFFERENTIAL or
 * NETWORK_SAME_SLAB_BONUS row, including an explicit zero-amount
 * differential row -- see CommissionCalculationEngine's own javadoc).
 * Created once, inside PlotSaleService.create()'s own transaction
 * (build-order step 5); never touched again by application code except to
 * flip {@code status} on cancellation (step 9, not built yet) -- released_amount
 * is entirely trigger-maintained from commission_release (V65_008).
 */
@Entity
@Table(name = "booking_commission")
public class BookingCommission {

    public enum LineType { SELLING_BROKER, UPLINE_DIFFERENTIAL, NETWORK_SAME_SLAB_BONUS }

    public enum Status { PENDING, PARTIALLY_RELEASED, FULLY_RELEASED, CANCELLED }

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

    @Column(name = "selling_broker_id", nullable = false)
    private UUID sellingBrokerId;

    @Column(name = "beneficiary_broker_id", nullable = false)
    private UUID beneficiaryBrokerId;

    @Column(name = "upline_level", nullable = false)
    private short uplineLevel;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.NAMED_ENUM)
    @Column(name = "commission_type", nullable = false)
    private LineType commissionType;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "rate_snapshot", nullable = false)
    private String rateSnapshot;

    @Column(name = "plot_area_sqft", nullable = false, precision = 14, scale = 4)
    private BigDecimal plotAreaSqft;

    @Column(name = "commission_per_sqft", nullable = false, precision = 19, scale = 2)
    private BigDecimal commissionPerSqft;

    @Column(name = "total_amount", nullable = false, precision = 19, scale = 2)
    private BigDecimal totalAmount;

    @Column(name = "released_amount", insertable = false, updatable = false, precision = 19, scale = 2)
    private BigDecimal releasedAmount;

    @Column(name = "paid_amount", insertable = false, updatable = false, precision = 19, scale = 2)
    private BigDecimal paidAmount;

    @Column(name = "pending_amount", insertable = false, updatable = false, precision = 19, scale = 2)
    private BigDecimal pendingAmount;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.NAMED_ENUM)
    @Column(nullable = false)
    private Status status = Status.PENDING;

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

    protected BookingCommission() {
    }

    public BookingCommission(UUID id, UUID orgId, UUID plotSaleId, UUID projectId, UUID plotId, UUID sellingBrokerId,
                              UUID beneficiaryBrokerId, short uplineLevel, LineType commissionType, String rateSnapshot,
                              BigDecimal plotAreaSqft, BigDecimal commissionPerSqft, BigDecimal totalAmount) {
        this.id = id;
        this.orgId = orgId;
        this.plotSaleId = plotSaleId;
        this.projectId = projectId;
        this.plotId = plotId;
        this.sellingBrokerId = sellingBrokerId;
        this.beneficiaryBrokerId = beneficiaryBrokerId;
        this.uplineLevel = uplineLevel;
        this.commissionType = commissionType;
        this.rateSnapshot = rateSnapshot;
        this.plotAreaSqft = plotAreaSqft;
        this.commissionPerSqft = commissionPerSqft;
        this.totalAmount = totalAmount;
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

    public UUID getSellingBrokerId() {
        return sellingBrokerId;
    }

    public UUID getBeneficiaryBrokerId() {
        return beneficiaryBrokerId;
    }

    public short getUplineLevel() {
        return uplineLevel;
    }

    public LineType getCommissionType() {
        return commissionType;
    }

    public String getRateSnapshot() {
        return rateSnapshot;
    }

    public BigDecimal getPlotAreaSqft() {
        return plotAreaSqft;
    }

    public BigDecimal getCommissionPerSqft() {
        return commissionPerSqft;
    }

    public BigDecimal getTotalAmount() {
        return totalAmount;
    }

    public BigDecimal getReleasedAmount() {
        return releasedAmount;
    }

    public BigDecimal getPaidAmount() {
        return paidAmount;
    }

    public BigDecimal getPendingAmount() {
        return pendingAmount;
    }

    public Status getStatus() {
        return status;
    }

    public void setStatus(Status status) {
        this.status = status;
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
