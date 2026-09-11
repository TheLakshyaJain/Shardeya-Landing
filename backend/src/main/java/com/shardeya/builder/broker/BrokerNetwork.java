package com.shardeya.builder.broker;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

import java.io.Serializable;
import java.util.Objects;
import java.util.UUID;

/**
 * 06-BROKER-NETWORK-ENGINE.md §10/§38 -- closure table. One row per
 * (ancestor, descendant) pair including a depth-0 self-row for every
 * broker. Maintained transactionally in Java (BrokerNetworkService), not
 * a DB trigger -- integrity validation (self/cycle/descendant checks)
 * must run BEFORE a row is written, not react after the fact.
 */
@Entity
@Table(name = "broker_network")
public class BrokerNetwork {

    @Embeddable
    public static class Key implements Serializable {
        @Column(name = "ancestor_broker_id")
        private UUID ancestorBrokerId;

        @Column(name = "descendant_broker_id")
        private UUID descendantBrokerId;

        protected Key() {
        }

        public Key(UUID ancestorBrokerId, UUID descendantBrokerId) {
            this.ancestorBrokerId = ancestorBrokerId;
            this.descendantBrokerId = descendantBrokerId;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (!(o instanceof Key key)) return false;
            return Objects.equals(ancestorBrokerId, key.ancestorBrokerId) && Objects.equals(descendantBrokerId, key.descendantBrokerId);
        }

        @Override
        public int hashCode() {
            return Objects.hash(ancestorBrokerId, descendantBrokerId);
        }
    }

    @EmbeddedId
    private Key id;

    @Column(name = "depth", nullable = false)
    private int depth;

    @Column(name = "org_id", nullable = false)
    private UUID orgId;

    protected BrokerNetwork() {
    }

    public BrokerNetwork(UUID ancestorBrokerId, UUID descendantBrokerId, int depth, UUID orgId) {
        this.id = new Key(ancestorBrokerId, descendantBrokerId);
        this.depth = depth;
        this.orgId = orgId;
    }

    public UUID getAncestorBrokerId() {
        return id.ancestorBrokerId;
    }

    public UUID getDescendantBrokerId() {
        return id.descendantBrokerId;
    }

    public int getDepth() {
        return depth;
    }

    public UUID getOrgId() {
        return orgId;
    }
}
