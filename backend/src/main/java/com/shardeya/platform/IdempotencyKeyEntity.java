package com.shardeya.platform;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.io.Serializable;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

@Entity
@Table(name = "idempotency_key")
public class IdempotencyKeyEntity {

    @EmbeddedId
    private Id id;

    @Column(name = "response_status", nullable = false)
    private int responseStatus;

    @Column(name = "response_body", nullable = false, columnDefinition = "jsonb")
    @JdbcTypeCode(SqlTypes.JSON)
    private String responseBody;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected IdempotencyKeyEntity() {
    }

    public IdempotencyKeyEntity(UUID orgId, String idempotencyKey, String endpoint, int responseStatus, String responseBody) {
        this.id = new Id(orgId, idempotencyKey, endpoint);
        this.responseStatus = responseStatus;
        this.responseBody = responseBody;
    }

    public Id getId() {
        return id;
    }

    public int getResponseStatus() {
        return responseStatus;
    }

    public String getResponseBody() {
        return responseBody;
    }

    @Embeddable
    public static class Id implements Serializable {
        @Column(name = "org_id")
        private UUID orgId;
        @Column(name = "idempotency_key")
        private String idempotencyKey;
        @Column(name = "endpoint")
        private String endpoint;

        protected Id() {
        }

        public Id(UUID orgId, String idempotencyKey, String endpoint) {
            this.orgId = orgId;
            this.idempotencyKey = idempotencyKey;
            this.endpoint = endpoint;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (!(o instanceof Id id)) return false;
            return Objects.equals(orgId, id.orgId) && Objects.equals(idempotencyKey, id.idempotencyKey) && Objects.equals(endpoint, id.endpoint);
        }

        @Override
        public int hashCode() {
            return Objects.hash(orgId, idempotencyKey, endpoint);
        }
    }
}
