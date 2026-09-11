package com.shardeya.builder.project;

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
@Table(name = "project")
public class Project {

    public enum Type { RESIDENTIAL_PLOT_COLONY, APARTMENT, VILLA, COMMERCIAL, MIXED_USE }

    public enum Status { UPCOMING, ACTIVE, COMPLETED }

    @Id
    private UUID id;

    @Column(name = "org_id", nullable = false)
    private UUID orgId;

    @Column(nullable = false, length = 150)
    private String name;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.NAMED_ENUM)
    @Column(name = "project_type", nullable = false)
    private Type projectType;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.NAMED_ENUM)
    @Column(nullable = false)
    private Status status = Status.UPCOMING;

    @Column(nullable = false, columnDefinition = "text")
    private String address;

    @Column(nullable = false, length = 150)
    private String locality;

    @Column(nullable = false, length = 100)
    private String city;

    @Column(name = "state_code", nullable = false, length = 2)
    private String stateCode;

    @Column(length = 6)
    private String pincode;

    @Column(name = "google_maps_url", columnDefinition = "text")
    private String googleMapsUrl;

    @Column(name = "total_area_value", nullable = false, precision = 14, scale = 4)
    private BigDecimal totalAreaValue;

    @Column(name = "total_area_unit", nullable = false, length = 16)
    private String totalAreaUnit;

    @Column(name = "total_area_sqft", nullable = false, precision = 14, scale = 4)
    private BigDecimal totalAreaSqft;

    @Column(name = "declared_plot_count", nullable = false)
    private int declaredPlotCount;

    @Column(name = "launch_date")
    private LocalDate launchDate;

    @Column(name = "expected_completion_date")
    private LocalDate expectedCompletionDate;

    @Column(columnDefinition = "text")
    private String description;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(nullable = false)
    private String approvals = "[]";

    @Column(name = "rera_number", length = 60)
    private String reraNumber;

    @Column(name = "cover_media_id")
    private UUID coverMediaId;

    @Column(name = "layout_media_id")
    private UUID layoutMediaId;

    @Column(name = "brochure_media_id")
    private UUID brochureMediaId;

    @Column(name = "grid_rows")
    private Integer gridRows;

    @Column(name = "grid_cols")
    private Integer gridCols;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "grid_layout", nullable = false)
    private String gridLayout = "{}";

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

    protected Project() {
    }

    public Project(UUID id, UUID orgId, String name, Type projectType, String address, String locality,
                    String city, String stateCode, BigDecimal totalAreaValue, String totalAreaUnit,
                    BigDecimal totalAreaSqft, int declaredPlotCount) {
        this.id = id;
        this.orgId = orgId;
        this.name = name;
        this.projectType = projectType;
        this.address = address;
        this.locality = locality;
        this.city = city;
        this.stateCode = stateCode;
        this.totalAreaValue = totalAreaValue;
        this.totalAreaUnit = totalAreaUnit;
        this.totalAreaSqft = totalAreaSqft;
        this.declaredPlotCount = declaredPlotCount;
    }

    public UUID getId() {
        return id;
    }

    public UUID getOrgId() {
        return orgId;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public Type getProjectType() {
        return projectType;
    }

    public void setProjectType(Type projectType) {
        this.projectType = projectType;
    }

    public Status getStatus() {
        return status;
    }

    public void setStatus(Status status) {
        this.status = status;
    }

    public String getAddress() {
        return address;
    }

    public void setAddress(String address) {
        this.address = address;
    }

    public String getLocality() {
        return locality;
    }

    public void setLocality(String locality) {
        this.locality = locality;
    }

    public String getCity() {
        return city;
    }

    public void setCity(String city) {
        this.city = city;
    }

    public String getStateCode() {
        return stateCode;
    }

    public void setStateCode(String stateCode) {
        this.stateCode = stateCode;
    }

    public String getPincode() {
        return pincode;
    }

    public void setPincode(String pincode) {
        this.pincode = pincode;
    }

    public String getGoogleMapsUrl() {
        return googleMapsUrl;
    }

    public void setGoogleMapsUrl(String googleMapsUrl) {
        this.googleMapsUrl = googleMapsUrl;
    }

    public BigDecimal getTotalAreaValue() {
        return totalAreaValue;
    }

    public void setTotalAreaValue(BigDecimal totalAreaValue) {
        this.totalAreaValue = totalAreaValue;
    }

    public String getTotalAreaUnit() {
        return totalAreaUnit;
    }

    public void setTotalAreaUnit(String totalAreaUnit) {
        this.totalAreaUnit = totalAreaUnit;
    }

    public BigDecimal getTotalAreaSqft() {
        return totalAreaSqft;
    }

    public void setTotalAreaSqft(BigDecimal totalAreaSqft) {
        this.totalAreaSqft = totalAreaSqft;
    }

    public int getDeclaredPlotCount() {
        return declaredPlotCount;
    }

    public void setDeclaredPlotCount(int declaredPlotCount) {
        this.declaredPlotCount = declaredPlotCount;
    }

    public LocalDate getLaunchDate() {
        return launchDate;
    }

    public void setLaunchDate(LocalDate launchDate) {
        this.launchDate = launchDate;
    }

    public LocalDate getExpectedCompletionDate() {
        return expectedCompletionDate;
    }

    public void setExpectedCompletionDate(LocalDate expectedCompletionDate) {
        this.expectedCompletionDate = expectedCompletionDate;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public String getApprovals() {
        return approvals;
    }

    public void setApprovals(String approvals) {
        this.approvals = approvals;
    }

    public String getReraNumber() {
        return reraNumber;
    }

    public void setReraNumber(String reraNumber) {
        this.reraNumber = reraNumber;
    }

    public UUID getCoverMediaId() {
        return coverMediaId;
    }

    public void setCoverMediaId(UUID coverMediaId) {
        this.coverMediaId = coverMediaId;
    }

    public UUID getLayoutMediaId() {
        return layoutMediaId;
    }

    public void setLayoutMediaId(UUID layoutMediaId) {
        this.layoutMediaId = layoutMediaId;
    }

    public UUID getBrochureMediaId() {
        return brochureMediaId;
    }

    public void setBrochureMediaId(UUID brochureMediaId) {
        this.brochureMediaId = brochureMediaId;
    }

    public Integer getGridRows() {
        return gridRows;
    }

    public void setGridRows(Integer gridRows) {
        this.gridRows = gridRows;
    }

    public Integer getGridCols() {
        return gridCols;
    }

    public void setGridCols(Integer gridCols) {
        this.gridCols = gridCols;
    }

    public String getGridLayout() {
        return gridLayout;
    }

    public void setGridLayout(String gridLayout) {
        this.gridLayout = gridLayout;
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
