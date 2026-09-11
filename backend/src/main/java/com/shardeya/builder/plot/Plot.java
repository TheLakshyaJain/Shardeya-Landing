package com.shardeya.builder.plot;

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
@Table(name = "plot")
public class Plot {

    public enum Status { AVAILABLE, RESERVED, SOLD }

    public enum Facing { N, S, E, W, NE, NW, SE, SW }

    @Id
    private UUID id;

    @Column(name = "org_id", nullable = false)
    private UUID orgId;

    @Column(name = "project_id", nullable = false)
    private UUID projectId;

    @Column(name = "plot_number", nullable = false, length = 30)
    private String plotNumber;

    @Column(name = "plot_number_norm", insertable = false, updatable = false)
    private String plotNumberNorm;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.NAMED_ENUM)
    @Column(nullable = false)
    private Status status = Status.AVAILABLE;

    @Column(name = "reserved_for", length = 150)
    private String reservedFor;

    @Column(name = "reserved_until")
    private LocalDate reservedUntil;

    @Column(name = "size_value", nullable = false, precision = 14, scale = 4)
    private BigDecimal sizeValue;

    @Column(name = "size_unit", nullable = false, length = 16)
    private String sizeUnit;

    @Column(name = "size_sqft", nullable = false, precision = 14, scale = 4)
    private BigDecimal sizeSqft;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.NAMED_ENUM)
    private Facing facing;

    @Column(nullable = false, precision = 19, scale = 2)
    private BigDecimal price;

    @Column(name = "price_per_unit", insertable = false, updatable = false, precision = 19, scale = 4)
    private BigDecimal pricePerUnit;

    @Column(name = "is_garden", nullable = false)
    private boolean garden;

    @Column(name = "is_corner", nullable = false)
    private boolean corner;

    @Column(name = "is_hot", nullable = false)
    private boolean hot;

    @Column(columnDefinition = "text")
    private String remarks;

    @Column(name = "grid_row")
    private Integer gridRow;

    @Column(name = "grid_col")
    private Integer gridCol;

    @Column(name = "current_sale_id")
    private UUID currentSaleId;

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

    protected Plot() {
    }

    public Plot(UUID id, UUID orgId, UUID projectId, String plotNumber, BigDecimal sizeValue, String sizeUnit,
                BigDecimal sizeSqft, BigDecimal price) {
        this.id = id;
        this.orgId = orgId;
        this.projectId = projectId;
        this.plotNumber = plotNumber;
        this.sizeValue = sizeValue;
        this.sizeUnit = sizeUnit;
        this.sizeSqft = sizeSqft;
        this.price = price;
    }

    public UUID getId() {
        return id;
    }

    public UUID getOrgId() {
        return orgId;
    }

    public UUID getProjectId() {
        return projectId;
    }

    public String getPlotNumber() {
        return plotNumber;
    }

    public void setPlotNumber(String plotNumber) {
        this.plotNumber = plotNumber;
    }

    public String getPlotNumberNorm() {
        return plotNumberNorm;
    }

    public Status getStatus() {
        return status;
    }

    public void setStatus(Status status) {
        this.status = status;
    }

    public String getReservedFor() {
        return reservedFor;
    }

    public void setReservedFor(String reservedFor) {
        this.reservedFor = reservedFor;
    }

    public LocalDate getReservedUntil() {
        return reservedUntil;
    }

    public void setReservedUntil(LocalDate reservedUntil) {
        this.reservedUntil = reservedUntil;
    }

    public BigDecimal getSizeValue() {
        return sizeValue;
    }

    public void setSizeValue(BigDecimal sizeValue) {
        this.sizeValue = sizeValue;
    }

    public String getSizeUnit() {
        return sizeUnit;
    }

    public void setSizeUnit(String sizeUnit) {
        this.sizeUnit = sizeUnit;
    }

    public BigDecimal getSizeSqft() {
        return sizeSqft;
    }

    public void setSizeSqft(BigDecimal sizeSqft) {
        this.sizeSqft = sizeSqft;
    }

    public Facing getFacing() {
        return facing;
    }

    public void setFacing(Facing facing) {
        this.facing = facing;
    }

    public BigDecimal getPrice() {
        return price;
    }

    public void setPrice(BigDecimal price) {
        this.price = price;
    }

    public BigDecimal getPricePerUnit() {
        return pricePerUnit;
    }

    public boolean isGarden() {
        return garden;
    }

    public void setGarden(boolean garden) {
        this.garden = garden;
    }

    public boolean isCorner() {
        return corner;
    }

    public void setCorner(boolean corner) {
        this.corner = corner;
    }

    public boolean isHot() {
        return hot;
    }

    public void setHot(boolean hot) {
        this.hot = hot;
    }

    public String getRemarks() {
        return remarks;
    }

    public void setRemarks(String remarks) {
        this.remarks = remarks;
    }

    public Integer getGridRow() {
        return gridRow;
    }

    public void setGridRow(Integer gridRow) {
        this.gridRow = gridRow;
    }

    public Integer getGridCol() {
        return gridCol;
    }

    public void setGridCol(Integer gridCol) {
        this.gridCol = gridCol;
    }

    public UUID getCurrentSaleId() {
        return currentSaleId;
    }

    public void setCurrentSaleId(UUID currentSaleId) {
        this.currentSaleId = currentSaleId;
    }

    public Instant getCreatedAt() {
        return createdAt;
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
