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
 * B-14 §20.4 -- genuinely per-org (V6_002's own comment): each BUILDER org
 * gets its own independently-editable Bronze/Silver/Gold/Platinum rows,
 * not a shared reference table like measurement_unit.
 */
@Entity
@Table(name = "broker_tier")
public class BrokerTier {

    public enum BonusType { PCT, FIXED, NONE }

    @Id
    private UUID id;

    @Column(name = "org_id", nullable = false)
    private UUID orgId;

    @Column(nullable = false, length = 40)
    private String name;

    @Column(name = "name_hi", length = 40)
    private String nameHi;

    @Column(name = "min_deals", nullable = false)
    private int minDeals;

    @Column(name = "max_deals")
    private Integer maxDeals;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.NAMED_ENUM)
    @Column(name = "bonus_type", nullable = false)
    private BonusType bonusType = BonusType.NONE;

    @Column(name = "bonus_value", nullable = false, precision = 19, scale = 3)
    private BigDecimal bonusValue = BigDecimal.ZERO;

    @Column(name = "badge_media_id")
    private UUID badgeMediaId;

    @Column(name = "perks_description", columnDefinition = "text")
    private String perksDescription;

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

    protected BrokerTier() {
    }

    public BrokerTier(UUID id, UUID orgId, String name, String nameHi, int minDeals, Integer maxDeals, short sortOrder) {
        this.id = id;
        this.orgId = orgId;
        this.name = name;
        this.nameHi = nameHi;
        this.minDeals = minDeals;
        this.maxDeals = maxDeals;
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

    public void setName(String name) {
        this.name = name;
    }

    public String getNameHi() {
        return nameHi;
    }

    public void setNameHi(String nameHi) {
        this.nameHi = nameHi;
    }

    public int getMinDeals() {
        return minDeals;
    }

    public void setMinDeals(int minDeals) {
        this.minDeals = minDeals;
    }

    public Integer getMaxDeals() {
        return maxDeals;
    }

    public void setMaxDeals(Integer maxDeals) {
        this.maxDeals = maxDeals;
    }

    public BonusType getBonusType() {
        return bonusType;
    }

    public void setBonusType(BonusType bonusType) {
        this.bonusType = bonusType;
    }

    public BigDecimal getBonusValue() {
        return bonusValue;
    }

    public void setBonusValue(BigDecimal bonusValue) {
        this.bonusValue = bonusValue;
    }

    public UUID getBadgeMediaId() {
        return badgeMediaId;
    }

    public void setBadgeMediaId(UUID badgeMediaId) {
        this.badgeMediaId = badgeMediaId;
    }

    public String getPerksDescription() {
        return perksDescription;
    }

    public void setPerksDescription(String perksDescription) {
        this.perksDescription = perksDescription;
    }

    public short getSortOrder() {
        return sortOrder;
    }

    public void setSortOrder(short sortOrder) {
        this.sortOrder = sortOrder;
    }

    public boolean isActive() {
        return active;
    }

    public void setActive(boolean active) {
        this.active = active;
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

    public void setDeletedBy(UUID deletedBy) {
        this.deletedBy = deletedBy;
    }

    public void setCreatedBy(UUID createdBy) {
        this.createdBy = createdBy;
    }

    public void setUpdatedBy(UUID updatedBy) {
        this.updatedBy = updatedBy;
    }

    /** True when [minDeals, maxDeals] would overlap the other tier's range -- used by service-layer overlap validation before the DB's own EXCLUDE constraint is hit, so the API can return a friendly error instead of a raw constraint-violation 500. */
    public boolean overlaps(int otherMin, Integer otherMax) {
        boolean thisUnbounded = maxDeals == null;
        boolean otherUnbounded = otherMax == null;
        int thisEnd = thisUnbounded ? Integer.MAX_VALUE : maxDeals;
        int otherEnd = otherUnbounded ? Integer.MAX_VALUE : otherMax;
        return minDeals <= otherEnd && otherMin <= thisEnd;
    }
}
