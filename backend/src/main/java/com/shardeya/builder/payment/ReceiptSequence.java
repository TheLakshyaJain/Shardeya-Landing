package com.shardeya.builder.payment;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

import java.io.Serializable;
import java.util.Objects;
import java.util.UUID;

@Entity
@Table(name = "receipt_sequence")
public class ReceiptSequence {

    @EmbeddedId
    private Id id;

    @Column(name = "next_value", nullable = false)
    private long nextValue = 1;

    protected ReceiptSequence() {
    }

    public ReceiptSequence(UUID orgId, String fy) {
        this.id = new Id(orgId, fy);
    }

    public Id getId() {
        return id;
    }

    public long getNextValue() {
        return nextValue;
    }

    public void setNextValue(long nextValue) {
        this.nextValue = nextValue;
    }

    @Embeddable
    public static class Id implements Serializable {
        @Column(name = "org_id")
        private UUID orgId;
        @Column(name = "fy")
        private String fy;

        protected Id() {
        }

        public Id(UUID orgId, String fy) {
            this.orgId = orgId;
            this.fy = fy;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (!(o instanceof Id id)) return false;
            return Objects.equals(orgId, id.orgId) && Objects.equals(fy, id.fy);
        }

        @Override
        public int hashCode() {
            return Objects.hash(orgId, fy);
        }
    }
}
