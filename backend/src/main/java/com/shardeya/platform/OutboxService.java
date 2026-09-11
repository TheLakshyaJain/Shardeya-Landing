package com.shardeya.platform;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.shardeya.foundation.notification.QuietHoursPolicy;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

/**
 * CLAUDE.md rule #6: "Business write + outbox insert in the same transaction."
 * Callers insert via this within their own {@code @Transactional} method —
 * inserting here does NOT open a new transaction, it participates in the
 * caller's.
 */
@Service
public class OutboxService {

    public static final String EVENT_TYPE_EMAIL = "EMAIL";
    public static final String EVENT_TYPE_NOTIFICATION = "NOTIFICATION";
    public static final String EVENT_TYPE_WHATSAPP = "WHATSAPP";
    public static final String EVENT_TYPE_SMS = "SMS";
    public static final String EVENT_TYPE_DOCUMENT_GENERATE = "DOCUMENT_GENERATE";

    private final OutboxEventRepository repository;
    private final ObjectMapper objectMapper;

    public OutboxService(OutboxEventRepository repository, ObjectMapper objectMapper) {
        this.repository = repository;
        this.objectMapper = objectMapper;
    }

    public void enqueueEmail(UUID orgId, String aggregateType, UUID aggregateId, String to, String subject, String body) {
        try {
            String payload = objectMapper.writeValueAsString(new EmailPayload(orgId, to, subject, body));
            repository.save(new OutboxEvent(UUID.randomUUID(), aggregateType, aggregateId, EVENT_TYPE_EMAIL, payload));
        } catch (Exception e) {
            throw new IllegalStateException("Failed to enqueue email outbox event", e);
        }
    }

    // M3: in-app notification fan-out. Aggregate is always the org itself
    // (aggregateType="ORGANIZATION", aggregateId=orgId) since
    // NotificationService.createForOrg fans out to every active user in
    // that org -- the individual notified users aren't known until dispatch
    // time queries them, so there's no single more-specific aggregate to
    // record here.
    public void enqueueNotification(UUID orgId, String typeCode, String titleKey, String bodyKey,
                                     Map<String, Object> params, String entityType, UUID entityId) {
        try {
            String payload = objectMapper.writeValueAsString(
                    new NotificationPayload(orgId, typeCode, titleKey, bodyKey, params, entityType, entityId));
            repository.save(new OutboxEvent(UUID.randomUUID(), "ORGANIZATION", orgId, EVENT_TYPE_NOTIFICATION, payload));
        } catch (Exception e) {
            throw new IllegalStateException("Failed to enqueue notification outbox event", e);
        }
    }

    /** M4: targets exactly one recipient (lead assignment, staff/team events) instead of broadcasting to the whole org -- see NotificationPayload's own javadoc. */
    public void enqueueNotificationForUser(UUID orgId, UUID recipientUserId, String typeCode, String titleKey,
                                            String bodyKey, Map<String, Object> params, String entityType, UUID entityId) {
        try {
            String payload = objectMapper.writeValueAsString(
                    new NotificationPayload(orgId, typeCode, titleKey, bodyKey, params, entityType, entityId, recipientUserId));
            repository.save(new OutboxEvent(UUID.randomUUID(), "APP_USER", recipientUserId, EVENT_TYPE_NOTIFICATION, payload));
        } catch (Exception e) {
            throw new IllegalStateException("Failed to enqueue notification outbox event", e);
        }
    }

    /** B-11 §17.2: auto-receipt generation on every payment_record insert, never inline (CLAUDE.md rule #6). */
    public void enqueueDocumentGenerate(UUID orgId, UUID paymentRecordId) {
        try {
            String payload = objectMapper.writeValueAsString(new DocumentGeneratePayload(orgId, paymentRecordId));
            repository.save(new OutboxEvent(UUID.randomUUID(), "PAYMENT_RECORD", paymentRecordId, EVENT_TYPE_DOCUMENT_GENERATE, payload));
        } catch (Exception e) {
            throw new IllegalStateException("Failed to enqueue document-generate outbox event", e);
        }
    }

    /**
     * M5: B-13 tracker reminders (follow-up due, overdue instalment); M-06
     * second half: every preference-driven WhatsApp send. Quiet hours
     * (M-06 §22: "no WhatsApp/SMS before 08:00 or after 21:00 IST; queued
     * to the next window") is applied HERE, centrally, for every caller --
     * not re-derived per call site -- so a message enqueued at 11pm simply
     * sits PENDING with available_at stamped to next-day 08:00 IST; the
     * poller's own findReadyToDispatch query does the rest, no separate
     * quiet-hours check needed at dispatch time.
     */
    public void enqueueWhatsApp(UUID orgId, String aggregateType, UUID aggregateId, String mobile, String text, String purpose) {
        try {
            String payload = objectMapper.writeValueAsString(new WhatsAppPayload(orgId, mobile, text, purpose));
            Instant availableAt = QuietHoursPolicy.nextAllowedInstant(Instant.now());
            repository.save(new OutboxEvent(UUID.randomUUID(), aggregateType, aggregateId, EVENT_TYPE_WHATSAPP, payload, availableAt));
        } catch (Exception e) {
            throw new IllegalStateException("Failed to enqueue WhatsApp outbox event", e);
        }
    }

    /** M-06 second half: preference-driven SMS + the critical-type WhatsApp-failure fallback. Same quiet-hours treatment as enqueueWhatsApp. */
    public void enqueueSms(UUID orgId, String aggregateType, UUID aggregateId, String mobile, String text, String purpose) {
        try {
            String payload = objectMapper.writeValueAsString(new SmsPayload(orgId, mobile, text, purpose));
            Instant availableAt = QuietHoursPolicy.nextAllowedInstant(Instant.now());
            repository.save(new OutboxEvent(UUID.randomUUID(), aggregateType, aggregateId, EVENT_TYPE_SMS, payload, availableAt));
        } catch (Exception e) {
            throw new IllegalStateException("Failed to enqueue SMS outbox event", e);
        }
    }
}
