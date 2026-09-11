package com.shardeya.platform;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.UUID;

/**
 * The single outbox table backing every side effect this app ever sends
 * (CLAUDE.md rule #6: business write + outbox insert in the same
 * transaction, never inline). Started M1-scope (EMAIL only); M-06's second
 * half (full WhatsApp/SMS/email + notification_preference-aware dispatch)
 * is what makes {@link OutboxService#EVENT_TYPE_SMS} and the rest of the
 * channel fan-out real. Retry backoff/DLQ is still a simple attempt cap
 * (status flips to FAILED, no separate DLQ table) -- that part was never
 * revisited and remains a deliberate simplification at this app's volume.
 */
@Entity
@Table(name = "outbox_event")
public class OutboxEvent {

    public enum Status { PENDING, PROCESSING, DONE, FAILED }

    @Id
    private UUID id;

    @Column(name = "aggregate_type", nullable = false, length = 60)
    private String aggregateType;

    @Column(name = "aggregate_id", nullable = false)
    private UUID aggregateId;

    @Column(name = "event_type", nullable = false, length = 60)
    private String eventType;

    @Column(nullable = false, columnDefinition = "jsonb")
    @org.hibernate.annotations.JdbcTypeCode(SqlTypes.JSON)
    private String payload;

    @Column(name = "available_at", nullable = false)
    private Instant availableAt;

    @Column(nullable = false)
    private short attempts;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.NAMED_ENUM)
    @Column(nullable = false)
    private Status status = Status.PENDING;

    @Column(name = "last_error")
    private String lastError;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected OutboxEvent() {
    }

    public OutboxEvent(UUID id, String aggregateType, UUID aggregateId, String eventType, String payloadJson) {
        this(id, aggregateType, aggregateId, eventType, payloadJson, Instant.now());
    }

    /** Quiet-hours deferral (M-06): a WhatsApp/SMS event enqueued outside 08:00-21:00 IST is stamped with its next allowed instant here, rather than "now" -- the poller's own findReadyToDispatch query then simply never picks it up early, no separate quiet-hours check needed at dispatch time. */
    public OutboxEvent(UUID id, String aggregateType, UUID aggregateId, String eventType, String payloadJson, Instant availableAt) {
        this.id = id;
        this.aggregateType = aggregateType;
        this.aggregateId = aggregateId;
        this.eventType = eventType;
        this.payload = payloadJson;
        this.availableAt = availableAt;
    }

    public UUID getId() {
        return id;
    }

    public String getEventType() {
        return eventType;
    }

    public String getPayload() {
        return payload;
    }

    public short getAttempts() {
        return attempts;
    }

    public void markProcessing() {
        this.status = Status.PROCESSING;
        this.attempts++;
    }

    public void markDone() {
        this.status = Status.DONE;
    }

    public void markFailed(String error) {
        this.status = Status.FAILED;
        this.lastError = error;
    }

    public void retryLater(String error, Instant nextAttemptAt) {
        this.status = Status.PENDING;
        this.lastError = error;
        this.availableAt = nextAttemptAt;
    }
}
