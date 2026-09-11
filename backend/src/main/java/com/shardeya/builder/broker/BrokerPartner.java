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

/** B-14 §20.1/§20.2 (01-DATA-MODEL.md §5). Owned by the builder org. */
@Entity
@Table(name = "broker_partner")
public class BrokerPartner {

    /** FIXED is retired for new brokers (06-BROKER-NETWORK-ENGINE.md §0) but kept for existing M6 data -- never removed from the enum. */
    public enum CommissionType { PERCENTAGE, FIXED, DESIGNATION }

    public enum Status { ACTIVE, INACTIVE, BLOCKED }

    @Id
    private UUID id;

    @Column(name = "org_id", nullable = false)
    private UUID orgId;

    @Column(name = "full_name", nullable = false, length = 120)
    private String fullName;

    @Column(nullable = false, length = 15)
    private String mobile;

    @Column(length = 255)
    private String email;

    @Column(name = "city_area", length = 150)
    private String cityArea;

    @Column(name = "rera_number", length = 60)
    private String reraNumber;

    @Column(name = "firm_name", length = 150)
    private String firmName;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.NAMED_ENUM)
    @Column(name = "commission_type", nullable = false)
    private CommissionType commissionType;

    @Column(name = "commission_pct", precision = 6, scale = 3)
    private BigDecimal commissionPct;

    @Column(name = "commission_fixed", precision = 19, scale = 2)
    private BigDecimal commissionFixed;

    @Column(name = "per_project_rates_enabled", nullable = false)
    private boolean perProjectRatesEnabled;

    @Column(name = "bank_account_name", length = 150)
    private String bankAccountName;

    @Column(name = "bank_account_number_enc")
    private byte[] bankAccountNumberEnc;

    @Column(name = "bank_account_last4", length = 4)
    private String bankAccountLast4;

    @Column(length = 11)
    private String ifsc;

    @Column(name = "upi_id", length = 80)
    private String upiId;

    @Column(columnDefinition = "text")
    private String notes;

    @Column(name = "tier_id")
    private UUID tierId;

    @Column(name = "tier_manually_overridden", nullable = false)
    private boolean tierManuallyOverridden;

    @Column(name = "tier_assigned_at")
    private Instant tierAssignedAt;

    @Column(name = "deals_closed_count", insertable = false, updatable = false)
    private int dealsClosedCount;

    @Column(name = "total_commission_earned", insertable = false, updatable = false, precision = 19, scale = 2)
    private BigDecimal totalCommissionEarned;

    @Column(name = "total_commission_paid", insertable = false, updatable = false, precision = 19, scale = 2)
    private BigDecimal totalCommissionPaid;

    @Column(name = "last_active_at")
    private Instant lastActiveAt;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.NAMED_ENUM)
    @Column(nullable = false)
    private Status status = Status.ACTIVE;

    @Column(name = "linked_user_id")
    private UUID linkedUserId;

    /** 06-BROKER-NETWORK-ENGINE.md §10/§11 -- DESIGNATION brokers only. At most one direct upline; NULL = top-level. */
    @Column(name = "upline_broker_id")
    private UUID uplineBrokerId;

    @Column(name = "current_designation_id")
    private UUID currentDesignationId;

    @Column(name = "current_commission_rate", precision = 19, scale = 2)
    private BigDecimal currentCommissionRate;

    @Column(name = "personal_successful_bookings", nullable = false, insertable = false, updatable = false)
    private int personalSuccessfulBookings;

    @Column(name = "team_successful_bookings", nullable = false, insertable = false, updatable = false)
    private int teamSuccessfulBookings;

    @Column(name = "designation_manually_overridden", nullable = false)
    private boolean designationManuallyOverridden;

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

    protected BrokerPartner() {
    }

    public BrokerPartner(UUID id, UUID orgId, String fullName, String mobile, CommissionType commissionType) {
        this.id = id;
        this.orgId = orgId;
        this.fullName = fullName;
        this.mobile = mobile;
        this.commissionType = commissionType;
    }

    public UUID getId() {
        return id;
    }

    public UUID getOrgId() {
        return orgId;
    }

    public String getFullName() {
        return fullName;
    }

    public void setFullName(String fullName) {
        this.fullName = fullName;
    }

    public String getMobile() {
        return mobile;
    }

    public void setMobile(String mobile) {
        this.mobile = mobile;
    }

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public String getCityArea() {
        return cityArea;
    }

    public void setCityArea(String cityArea) {
        this.cityArea = cityArea;
    }

    public String getReraNumber() {
        return reraNumber;
    }

    public void setReraNumber(String reraNumber) {
        this.reraNumber = reraNumber;
    }

    public String getFirmName() {
        return firmName;
    }

    public void setFirmName(String firmName) {
        this.firmName = firmName;
    }

    public CommissionType getCommissionType() {
        return commissionType;
    }

    public void setCommissionType(CommissionType commissionType) {
        this.commissionType = commissionType;
    }

    public BigDecimal getCommissionPct() {
        return commissionPct;
    }

    public void setCommissionPct(BigDecimal commissionPct) {
        this.commissionPct = commissionPct;
    }

    public BigDecimal getCommissionFixed() {
        return commissionFixed;
    }

    public void setCommissionFixed(BigDecimal commissionFixed) {
        this.commissionFixed = commissionFixed;
    }

    public boolean isPerProjectRatesEnabled() {
        return perProjectRatesEnabled;
    }

    public void setPerProjectRatesEnabled(boolean perProjectRatesEnabled) {
        this.perProjectRatesEnabled = perProjectRatesEnabled;
    }

    public String getBankAccountName() {
        return bankAccountName;
    }

    public void setBankAccountName(String bankAccountName) {
        this.bankAccountName = bankAccountName;
    }

    public byte[] getBankAccountNumberEnc() {
        return bankAccountNumberEnc;
    }

    public void setBankAccountNumberEnc(byte[] bankAccountNumberEnc) {
        this.bankAccountNumberEnc = bankAccountNumberEnc;
    }

    public String getBankAccountLast4() {
        return bankAccountLast4;
    }

    public void setBankAccountLast4(String bankAccountLast4) {
        this.bankAccountLast4 = bankAccountLast4;
    }

    public String getIfsc() {
        return ifsc;
    }

    public void setIfsc(String ifsc) {
        this.ifsc = ifsc;
    }

    public String getUpiId() {
        return upiId;
    }

    public void setUpiId(String upiId) {
        this.upiId = upiId;
    }

    public String getNotes() {
        return notes;
    }

    public void setNotes(String notes) {
        this.notes = notes;
    }

    public UUID getTierId() {
        return tierId;
    }

    public void setTierId(UUID tierId) {
        this.tierId = tierId;
    }

    public boolean isTierManuallyOverridden() {
        return tierManuallyOverridden;
    }

    public void setTierManuallyOverridden(boolean tierManuallyOverridden) {
        this.tierManuallyOverridden = tierManuallyOverridden;
    }

    public Instant getTierAssignedAt() {
        return tierAssignedAt;
    }

    public void setTierAssignedAt(Instant tierAssignedAt) {
        this.tierAssignedAt = tierAssignedAt;
    }

    public int getDealsClosedCount() {
        return dealsClosedCount;
    }

    public BigDecimal getTotalCommissionEarned() {
        return totalCommissionEarned;
    }

    public BigDecimal getTotalCommissionPaid() {
        return totalCommissionPaid;
    }

    public Instant getLastActiveAt() {
        return lastActiveAt;
    }

    public void setLastActiveAt(Instant lastActiveAt) {
        this.lastActiveAt = lastActiveAt;
    }

    public Status getStatus() {
        return status;
    }

    public void setStatus(Status status) {
        this.status = status;
    }

    public UUID getLinkedUserId() {
        return linkedUserId;
    }

    public void setLinkedUserId(UUID linkedUserId) {
        this.linkedUserId = linkedUserId;
    }

    public UUID getUplineBrokerId() {
        return uplineBrokerId;
    }

    public void setUplineBrokerId(UUID uplineBrokerId) {
        this.uplineBrokerId = uplineBrokerId;
    }

    public UUID getCurrentDesignationId() {
        return currentDesignationId;
    }

    public void setCurrentDesignationId(UUID currentDesignationId) {
        this.currentDesignationId = currentDesignationId;
    }

    public BigDecimal getCurrentCommissionRate() {
        return currentCommissionRate;
    }

    public void setCurrentCommissionRate(BigDecimal currentCommissionRate) {
        this.currentCommissionRate = currentCommissionRate;
    }

    public int getPersonalSuccessfulBookings() {
        return personalSuccessfulBookings;
    }

    public int getTeamSuccessfulBookings() {
        return teamSuccessfulBookings;
    }

    public boolean isDesignationManuallyOverridden() {
        return designationManuallyOverridden;
    }

    public void setDesignationManuallyOverridden(boolean designationManuallyOverridden) {
        this.designationManuallyOverridden = designationManuallyOverridden;
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

    public void setDeletedAt(Instant deletedAt) {
        this.deletedAt = deletedAt;
    }

    public void setDeletedBy(UUID deletedBy) {
        this.deletedBy = deletedBy;
    }
}
