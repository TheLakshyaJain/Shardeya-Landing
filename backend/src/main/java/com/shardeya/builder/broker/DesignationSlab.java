package com.shardeya.builder.broker;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * 06-BROKER-NETWORK-ENGINE.md §5/§11 -- the 8-level designation/rate
 * table for DESIGNATION-type brokers. {@code orgId} is nullable: NULL
 * rows are system-wide defaults (seeded once, V65_002), a non-null value
 * is a future per-org override -- same shape as {@code Role.orgId}. No v1
 * write path ever produces a non-null row.
 */
@Entity
@Table(name = "designation_slab")
public class DesignationSlab {

    @Id
    private UUID id;

    @Column(name = "org_id")
    private UUID orgId;

    @Column(nullable = false, length = 60)
    private String name;

    @Column(name = "name_hi", length = 60)
    private String nameHi;

    @Column(name = "min_team_sales", nullable = false)
    private int minTeamSales;

    @Column(name = "max_team_sales")
    private Integer maxTeamSales;

    @Column(name = "rate_per_sqft", nullable = false, precision = 19, scale = 2)
    private BigDecimal ratePerSqft;

    @Column(name = "sort_order", nullable = false)
    private short sortOrder;

    @Column(name = "is_active", nullable = false)
    private boolean active = true;

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

    protected DesignationSlab() {
    }

    public DesignationSlab(UUID id, UUID orgId, String name, String nameHi, int minTeamSales, Integer maxTeamSales,
                            BigDecimal ratePerSqft, short sortOrder) {
        this.id = id;
        this.orgId = orgId;
        this.name = name;
        this.nameHi = nameHi;
        this.minTeamSales = minTeamSales;
        this.maxTeamSales = maxTeamSales;
        this.ratePerSqft = ratePerSqft;
        this.sortOrder = sortOrder;
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

    public String getNameHi() {
        return nameHi;
    }

    public int getMinTeamSales() {
        return minTeamSales;
    }

    public Integer getMaxTeamSales() {
        return maxTeamSales;
    }

    public BigDecimal getRatePerSqft() {
        return ratePerSqft;
    }

    public void setRatePerSqft(BigDecimal ratePerSqft) {
        this.ratePerSqft = ratePerSqft;
    }

    public short getSortOrder() {
        return sortOrder;
    }

    public boolean isActive() {
        return active;
    }

    public Instant getDeletedAt() {
        return deletedAt;
    }

    /** Same overlap-detection shape as {@link BrokerTier#overlaps} -- used for a future per-org override create/edit path. */
    public boolean overlaps(int otherMin, Integer otherMax) {
        boolean thisUnbounded = maxTeamSales == null;
        boolean otherUnbounded = otherMax == null;
        int thisEnd = thisUnbounded ? Integer.MAX_VALUE : maxTeamSales;
        int otherEnd = otherUnbounded ? Integer.MAX_VALUE : otherMax;
        return minTeamSales <= otherEnd && otherMin <= thisEnd;
    }
}
