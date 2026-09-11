package com.shardeya.builder.broker;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/** B-14 §20.6 "follow-up history and notes" on a broker's profile -- same purpose as foundation.customer's Interaction, distinct table since brokers and leads share no parent. */
@Entity
@Table(name = "broker_interaction")
public class BrokerInteraction {

    @Id
    private UUID id;

    @Column(name = "org_id", nullable = false)
    private UUID orgId;

    @Column(name = "broker_partner_id", nullable = false)
    private UUID brokerPartnerId;

    @Column(name = "occurred_on", nullable = false)
    private LocalDate occurredOn;

    @Column(length = 20)
    private String type;

    @Column(nullable = false, columnDefinition = "text")
    private String remarks;

    @Column(name = "next_follow_up_date")
    private LocalDate nextFollowUpDate;

    @Column(name = "conducted_by")
    private UUID conductedBy;

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

    protected BrokerInteraction() {
    }

    public BrokerInteraction(UUID id, UUID orgId, UUID brokerPartnerId, LocalDate occurredOn, String type,
                              String remarks, LocalDate nextFollowUpDate, UUID conductedBy) {
        this.id = id;
        this.orgId = orgId;
        this.brokerPartnerId = brokerPartnerId;
        this.occurredOn = occurredOn;
        this.type = type;
        this.remarks = remarks;
        this.nextFollowUpDate = nextFollowUpDate;
        this.conductedBy = conductedBy;
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

    public LocalDate getOccurredOn() {
        return occurredOn;
    }

    public String getType() {
        return type;
    }

    public String getRemarks() {
        return remarks;
    }

    public LocalDate getNextFollowUpDate() {
        return nextFollowUpDate;
    }

    public UUID getConductedBy() {
        return conductedBy;
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
