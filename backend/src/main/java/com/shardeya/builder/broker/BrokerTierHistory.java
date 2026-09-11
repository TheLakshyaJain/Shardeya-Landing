package com.shardeya.builder.broker;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.hibernate.annotations.CreationTimestamp;

import java.time.Instant;
import java.util.UUID;

/** B-14 §3 -- append-only audit trail of every tier change, auto-evaluated or manual override. Never updated once written. */
@Entity
@Table(name = "broker_tier_history")
public class BrokerTierHistory {

    @Id
    private UUID id;

    @Column(name = "org_id", nullable = false)
    private UUID orgId;

    @Column(name = "broker_partner_id", nullable = false)
    private UUID brokerPartnerId;

    @Column(name = "from_tier_id")
    private UUID fromTierId;

    @Column(name = "to_tier_id", nullable = false)
    private UUID toTierId;

    @Column(name = "deals_at_change", nullable = false)
    private int dealsAtChange;

    @CreationTimestamp
    @Column(name = "changed_at", nullable = false, updatable = false)
    private Instant changedAt;

    @Column(name = "changed_by")
    private UUID changedBy;

    @Column(name = "is_manual", nullable = false)
    private boolean manual;

    @Column(columnDefinition = "text")
    private String reason;

    protected BrokerTierHistory() {
    }

    public BrokerTierHistory(UUID id, UUID orgId, UUID brokerPartnerId, UUID fromTierId, UUID toTierId,
                              int dealsAtChange, UUID changedBy, boolean manual, String reason) {
        this.id = id;
        this.orgId = orgId;
        this.brokerPartnerId = brokerPartnerId;
        this.fromTierId = fromTierId;
        this.toTierId = toTierId;
        this.dealsAtChange = dealsAtChange;
        this.changedBy = changedBy;
        this.manual = manual;
        this.reason = reason;
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

    public UUID getFromTierId() {
        return fromTierId;
    }

    public UUID getToTierId() {
        return toTierId;
    }

    public int getDealsAtChange() {
        return dealsAtChange;
    }

    public Instant getChangedAt() {
        return changedAt;
    }

    public UUID getChangedBy() {
        return changedBy;
    }

    public boolean isManual() {
        return manual;
    }

    public String getReason() {
        return reason;
    }
}
