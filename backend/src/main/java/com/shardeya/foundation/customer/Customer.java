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

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/**
 * M-12 Customer / Lead Core -- one entity shared by broker (§6) and builder
 * (§13) profiles; the builder-only columns (interestedProjectId,
 * interestedPlotId, assignedTo, sourceBrokerId) are simply null for broker
 * orgs. sourceBrokerId has no FK yet -- broker_partner (B-14) doesn't exist
 * until a later milestone, same deferred-FK pattern V3_001 already used for
 * plot_sale.broker_partner_id.
 */
@Entity
@Table(name = "customer")
public class Customer {

    public enum PreferredPropertyType { PLOT, FLAT, HOUSE }

    public enum Source {
        REFERRAL, FACEBOOK, INSTAGRAM, WALK_IN, COLD_CALL, WEBSITE, BROKER, EXHIBITION, SOCIAL_MEDIA, OTHER
    }

    public enum Status {
        INTERESTED, SITE_VISIT_SCHEDULED, SITE_VISIT_DONE, FOLLOWING_UP, DEAL_CLOSED, LOST
    }

    @Id
    private UUID id;

    @Column(name = "org_id", nullable = false)
    private UUID orgId;

    @Column(name = "full_name", nullable = false, length = 120)
    private String fullName;

    @Column(nullable = false, length = 15)
    private String mobile;

    @Column(name = "alternate_mobile", length = 15)
    private String alternateMobile;

    @Column(length = 255)
    private String email;

    @Column(name = "budget_min", nullable = false, precision = 19, scale = 2)
    private BigDecimal budgetMin;

    @Column(name = "budget_max", nullable = false, precision = 19, scale = 2)
    private BigDecimal budgetMax;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.NAMED_ENUM)
    @Column(name = "preferred_property_type")
    private PreferredPropertyType preferredPropertyType;

    @Column(name = "preferred_locality", length = 150)
    private String preferredLocality;

    @Column(name = "size_requirement", length = 80)
    private String sizeRequirement;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.NAMED_ENUM)
    @Column(nullable = false)
    private Source source;

    @Column(name = "source_broker_id")
    private UUID sourceBrokerId;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.NAMED_ENUM)
    @Column(nullable = false)
    private Status status = Status.INTERESTED;

    @Column(name = "interested_project_id")
    private UUID interestedProjectId;

    @Column(name = "interested_plot_id")
    private UUID interestedPlotId;

    @Column(name = "assigned_to")
    private UUID assignedTo;

    @Column(name = "follow_up_date")
    private LocalDate followUpDate;

    @Column(name = "site_visit_date")
    private LocalDate siteVisitDate;

    @Column(name = "no_further_follow_up", nullable = false)
    private boolean noFurtherFollowUp;

    @Column(name = "is_important", nullable = false)
    private boolean important;

    @Column(columnDefinition = "text")
    private String remarks;

    @Column(name = "last_interaction_at")
    private Instant lastInteractionAt;

    @Column(name = "closed_at")
    private LocalDate closedAt;

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

    protected Customer() {
    }

    public Customer(UUID id, UUID orgId, String fullName, String mobile, BigDecimal budgetMin, BigDecimal budgetMax,
                     Source source) {
        this.id = id;
        this.orgId = orgId;
        this.fullName = fullName;
        this.mobile = mobile;
        this.budgetMin = budgetMin;
        this.budgetMax = budgetMax;
        this.source = source;
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

    public String getAlternateMobile() {
        return alternateMobile;
    }

    public void setAlternateMobile(String alternateMobile) {
        this.alternateMobile = alternateMobile;
    }

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public BigDecimal getBudgetMin() {
        return budgetMin;
    }

    public void setBudgetMin(BigDecimal budgetMin) {
        this.budgetMin = budgetMin;
    }

    public BigDecimal getBudgetMax() {
        return budgetMax;
    }

    public void setBudgetMax(BigDecimal budgetMax) {
        this.budgetMax = budgetMax;
    }

    public PreferredPropertyType getPreferredPropertyType() {
        return preferredPropertyType;
    }

    public void setPreferredPropertyType(PreferredPropertyType preferredPropertyType) {
        this.preferredPropertyType = preferredPropertyType;
    }

    public String getPreferredLocality() {
        return preferredLocality;
    }

    public void setPreferredLocality(String preferredLocality) {
        this.preferredLocality = preferredLocality;
    }

    public String getSizeRequirement() {
        return sizeRequirement;
    }

    public void setSizeRequirement(String sizeRequirement) {
        this.sizeRequirement = sizeRequirement;
    }

    public Source getSource() {
        return source;
    }

    public void setSource(Source source) {
        this.source = source;
    }

    public UUID getSourceBrokerId() {
        return sourceBrokerId;
    }

    public void setSourceBrokerId(UUID sourceBrokerId) {
        this.sourceBrokerId = sourceBrokerId;
    }

    public Status getStatus() {
        return status;
    }

    public void setStatus(Status status) {
        this.status = status;
    }

    public UUID getInterestedProjectId() {
        return interestedProjectId;
    }

    public void setInterestedProjectId(UUID interestedProjectId) {
        this.interestedProjectId = interestedProjectId;
    }

    public UUID getInterestedPlotId() {
        return interestedPlotId;
    }

    public void setInterestedPlotId(UUID interestedPlotId) {
        this.interestedPlotId = interestedPlotId;
    }

    public UUID getAssignedTo() {
        return assignedTo;
    }

    public void setAssignedTo(UUID assignedTo) {
        this.assignedTo = assignedTo;
    }

    public LocalDate getFollowUpDate() {
        return followUpDate;
    }

    public void setFollowUpDate(LocalDate followUpDate) {
        this.followUpDate = followUpDate;
    }

    public LocalDate getSiteVisitDate() {
        return siteVisitDate;
    }

    public void setSiteVisitDate(LocalDate siteVisitDate) {
        this.siteVisitDate = siteVisitDate;
    }

    public boolean isNoFurtherFollowUp() {
        return noFurtherFollowUp;
    }

    public void setNoFurtherFollowUp(boolean noFurtherFollowUp) {
        this.noFurtherFollowUp = noFurtherFollowUp;
    }

    public boolean isImportant() {
        return important;
    }

    public void setImportant(boolean important) {
        this.important = important;
    }

    public String getRemarks() {
        return remarks;
    }

    public void setRemarks(String remarks) {
        this.remarks = remarks;
    }

    public Instant getLastInteractionAt() {
        return lastInteractionAt;
    }

    public void setLastInteractionAt(Instant lastInteractionAt) {
        this.lastInteractionAt = lastInteractionAt;
    }

    public LocalDate getClosedAt() {
        return closedAt;
    }

    public void setClosedAt(LocalDate closedAt) {
        this.closedAt = closedAt;
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
