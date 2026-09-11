package com.shardeya.platform;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.shardeya.builder.document.DocumentGenerationService;
import com.shardeya.foundation.auth.SmsGateway;
import com.shardeya.foundation.notification.EmailGateway;
import com.shardeya.foundation.notification.MessageDelivery;
import com.shardeya.foundation.notification.MessageDeliveryRepository;
import com.shardeya.foundation.notification.NotificationDispatchService;
import com.shardeya.foundation.notification.WhatsAppGateway;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * Outbox dispatcher for all five event types (EMAIL, NOTIFICATION,
 * WHATSAPP, SMS, DOCUMENT_GENERATE) -- see {@link OutboxEvent}'s javadoc
 * for the original M1-era EMAIL-only scope this has grown from.
 *
 * <p><b>Per-tick rate limiting (M-06 §22, "basic rate limiting so we don't
 * blow through a WhatsApp sending quota by accident"):</b> a simple dispatch
 * cap per {@link #dispatchReady()} tick, not a Redis sliding window -- an
 * event that would exceed the cap is left completely untouched (still
 * {@code PENDING}, {@code attempts} not incremented, no retry-backoff
 * applied) and is simply reconsidered on the next 2-second tick, alongside
 * whatever else has since become ready. At {@link #MAX_WHATSAPP_PER_TICK}/
 * {@link #MAX_SMS_PER_TICK} per 2s tick that's a generous cap for this
 * app's realistic per-org volume while still bounding a runaway burst (a
 * bulk operation that fans out hundreds of reminders at once, say).
 */
@Component
public class OutboxPoller {

    private static final Logger log = LoggerFactory.getLogger(OutboxPoller.class);
    private static final int MAX_ATTEMPTS = 5;
    private static final int MAX_WHATSAPP_PER_TICK = 20;
    private static final int MAX_SMS_PER_TICK = 20;

    private static final Set<String> CRITICAL_TYPES = Set.of(
            "PAYMENT_RECORDED", "INSTALMENT_OVERDUE", "INSTALMENT_DUE_TODAY", "CHEQUE_BOUNCED");

    // A "system" user id for the synthetic tenant context bound below --
    // only orgId matters for the RLS check this exists to satisfy. Never
    // persisted into a real foreign key from this class (see
    // DocumentGenerationService's own notes on why that specifically would
    // be wrong) -- message_delivery/notification rows here always carry a
    // real recipient, never this placeholder.
    private static final UUID SYSTEM_ACTOR_ID = new UUID(0, 0);

    private final OutboxEventRepository repository;
    private final ObjectMapper objectMapper;
    private final EmailGateway emailGateway;
    private final NotificationDispatchService notificationDispatchService;
    private final WhatsAppGateway whatsAppGateway;
    private final SmsGateway smsGateway;
    private final MessageDeliveryRepository messageDeliveryRepository;
    private final DocumentGenerationService documentGenerationService;
    private final TenantContextBinder tenantContextBinder;
    private final TransactionTemplate perOrgTransaction;

    public OutboxPoller(OutboxEventRepository repository, ObjectMapper objectMapper, EmailGateway emailGateway,
                         NotificationDispatchService notificationDispatchService, WhatsAppGateway whatsAppGateway,
                         SmsGateway smsGateway, MessageDeliveryRepository messageDeliveryRepository,
                         DocumentGenerationService documentGenerationService,
                         TenantContextBinder tenantContextBinder, PlatformTransactionManager transactionManager) {
        this.repository = repository;
        this.objectMapper = objectMapper;
        this.emailGateway = emailGateway;
        this.notificationDispatchService = notificationDispatchService;
        this.whatsAppGateway = whatsAppGateway;
        this.smsGateway = smsGateway;
        this.messageDeliveryRepository = messageDeliveryRepository;
        this.documentGenerationService = documentGenerationService;
        this.tenantContextBinder = tenantContextBinder;
        // PROPAGATION_REQUIRES_NEW: dispatchReady()'s own @Transactional is
        // already open (with no tenant bound -- outbox_event itself has no
        // org_id/RLS) by the time dispatch() runs for any given event. The
        // notification/message_delivery insert needs its OWN connection
        // checkout, made AFTER binding this event's org, for
        // TenantAwareDataSource to fix the right app.current_org GUC on it --
        // exactly the "bind before the transaction opens" rule
        // TenantContextBinder's own javadoc documents, applied here via a
        // nested transaction instead of a fresh top-level one.
        this.perOrgTransaction = new TransactionTemplate(transactionManager);
        this.perOrgTransaction.setPropagationBehavior(org.springframework.transaction.TransactionDefinition.PROPAGATION_REQUIRES_NEW);
    }

    @Scheduled(fixedDelay = 2000)
    @Transactional
    public void dispatchReady() {
        List<OutboxEvent> ready = repository.findReadyToDispatch(OutboxEvent.Status.PENDING, Instant.now());
        int whatsappDispatched = 0;
        int smsDispatched = 0;
        for (OutboxEvent event : ready) {
            if (OutboxService.EVENT_TYPE_WHATSAPP.equals(event.getEventType())) {
                if (whatsappDispatched >= MAX_WHATSAPP_PER_TICK) {
                    continue;
                }
                whatsappDispatched++;
            } else if (OutboxService.EVENT_TYPE_SMS.equals(event.getEventType())) {
                if (smsDispatched >= MAX_SMS_PER_TICK) {
                    continue;
                }
                smsDispatched++;
            }
            dispatch(event);
        }
    }

    private void dispatch(OutboxEvent event) {
        event.markProcessing();
        try {
            if (OutboxService.EVENT_TYPE_EMAIL.equals(event.getEventType())) {
                sendEmail(event);
            } else if (OutboxService.EVENT_TYPE_NOTIFICATION.equals(event.getEventType())) {
                sendNotification(event);
            } else if (OutboxService.EVENT_TYPE_WHATSAPP.equals(event.getEventType())) {
                sendWhatsApp(event);
            } else if (OutboxService.EVENT_TYPE_SMS.equals(event.getEventType())) {
                sendSms(event);
            } else if (OutboxService.EVENT_TYPE_DOCUMENT_GENERATE.equals(event.getEventType())) {
                generateDocument(event);
            }
            event.markDone();
        } catch (Exception e) {
            log.warn("Outbox event {} dispatch failed (attempt {})", event.getId(), event.getAttempts(), e);
            if (event.getAttempts() >= MAX_ATTEMPTS) {
                event.markFailed(e.getMessage());
            } else {
                event.retryLater(e.getMessage(), Instant.now().plusSeconds(10L * event.getAttempts()));
            }
        }
    }

    private void sendEmail(OutboxEvent event) throws Exception {
        EmailPayload payload = objectMapper.readValue(event.getPayload(), EmailPayload.class);
        tenantContextBinder.bindNewOrgContext(payload.orgId(), SYSTEM_ACTOR_ID, "SYSTEM", "SYSTEM", Set.of());
        try {
            perOrgTransaction.executeWithoutResult(status -> {
                MessageDelivery delivery = new MessageDelivery(UUID.randomUUID(), payload.orgId(), MessageDelivery.Channel.EMAIL,
                        maskEmail(payload.to()), null, emailGateway.getClass().getSimpleName(), null);
                try {
                    emailGateway.send(payload.to(), payload.subject(), payload.body());
                    delivery.markSent(null);
                } catch (RuntimeException e) {
                    delivery.markFailed(e.getMessage());
                    messageDeliveryRepository.save(delivery);
                    throw e;
                }
                messageDeliveryRepository.save(delivery);
            });
        } finally {
            tenantContextBinder.clear();
        }
    }

    private void sendWhatsApp(OutboxEvent event) throws Exception {
        WhatsAppPayload payload = objectMapper.readValue(event.getPayload(), WhatsAppPayload.class);
        tenantContextBinder.bindNewOrgContext(payload.orgId(), SYSTEM_ACTOR_ID, "SYSTEM", "SYSTEM", Set.of());
        try {
            perOrgTransaction.executeWithoutResult(status -> {
                MessageDelivery delivery = new MessageDelivery(UUID.randomUUID(), payload.orgId(), MessageDelivery.Channel.WHATSAPP,
                        maskMobile(payload.mobile()), payload.purpose(), whatsAppGateway.getClass().getSimpleName(), null);
                try {
                    WhatsAppGateway.SendResult result = whatsAppGateway.sendText(payload.mobile(), payload.text(), payload.purpose());
                    delivery.markSent(result.providerMessageId());
                    messageDeliveryRepository.save(delivery);
                } catch (RuntimeException e) {
                    delivery.markFailed(e.getMessage());
                    messageDeliveryRepository.save(delivery);
                    // §22.1 fallback: a WhatsApp SEND failure (as opposed to
                    // "never opted in", handled earlier in
                    // NotificationDispatchService) for a critical type still
                    // deserves a safety-net SMS -- best-effort, its own
                    // failure doesn't mask the original WhatsApp exception
                    // below, which still drives this event's own retry/DLQ.
                    if (CRITICAL_TYPES.contains(payload.purpose())) {
                        try {
                            outboxServiceEnqueueSmsFallback(payload);
                        } catch (RuntimeException fallbackEx) {
                            log.warn("WhatsApp-failure SMS fallback also failed for org {}", payload.orgId(), fallbackEx);
                        }
                    }
                    throw e;
                }
            });
        } finally {
            tenantContextBinder.clear();
        }
    }

    // Deliberately re-enqueues through the outbox (rather than sending
    // inline here) so the fallback SMS gets the same quiet-hours/rate-limit/
    // retry treatment as every other SMS -- CLAUDE.md rule #6 applies to a
    // fallback exactly as much as to the original trigger.
    private void outboxServiceEnqueueSmsFallback(WhatsAppPayload payload) {
        try {
            String smsPayload = objectMapper.writeValueAsString(new SmsPayload(payload.orgId(), payload.mobile(), payload.text(), payload.purpose()));
            // aggregate_id is NOT NULL and there's no real entity id to hand
            // in at this point (only a mobile number/purpose string) -- a
            // fresh synthetic id is fine, this row is never looked up by
            // aggregate, only ever polled by status/available_at.
            repository.save(new OutboxEvent(UUID.randomUUID(), "APP_USER", UUID.randomUUID(), OutboxService.EVENT_TYPE_SMS, smsPayload));
        } catch (Exception e) {
            throw new IllegalStateException("Failed to enqueue WhatsApp-failure SMS fallback", e);
        }
    }

    private void sendSms(OutboxEvent event) throws Exception {
        SmsPayload payload = objectMapper.readValue(event.getPayload(), SmsPayload.class);
        tenantContextBinder.bindNewOrgContext(payload.orgId(), SYSTEM_ACTOR_ID, "SYSTEM", "SYSTEM", Set.of());
        try {
            perOrgTransaction.executeWithoutResult(status -> {
                MessageDelivery delivery = new MessageDelivery(UUID.randomUUID(), payload.orgId(), MessageDelivery.Channel.SMS,
                        maskMobile(payload.mobile()), payload.purpose(), smsGateway.getClass().getSimpleName(), null);
                try {
                    smsGateway.sendText(payload.mobile(), payload.text(), payload.purpose());
                    delivery.markSent(null);
                } catch (RuntimeException e) {
                    delivery.markFailed(e.getMessage());
                    messageDeliveryRepository.save(delivery);
                    throw e;
                }
                messageDeliveryRepository.save(delivery);
            });
        } finally {
            tenantContextBinder.clear();
        }
    }

    // Same bind-before-transaction ordering as sendNotification() -- see
    // that method's own comment and TenantContextBinder's javadoc for why
    // this is load-bearing, not stylistic.
    private void generateDocument(OutboxEvent event) throws Exception {
        DocumentGeneratePayload payload = objectMapper.readValue(event.getPayload(), DocumentGeneratePayload.class);
        tenantContextBinder.bindNewOrgContext(payload.orgId(), SYSTEM_ACTOR_ID, "SYSTEM", "SYSTEM", Set.of());
        try {
            perOrgTransaction.executeWithoutResult(status ->
                    documentGenerationService.autoGenerateReceipt(payload.orgId(), payload.paymentRecordId()));
        } finally {
            tenantContextBinder.clear();
        }
    }

    private void sendNotification(OutboxEvent event) throws Exception {
        NotificationPayload payload = objectMapper.readValue(event.getPayload(), NotificationPayload.class);
        // Bind in plain Java, BEFORE perOrgTransaction.executeWithoutResult
        // opens its own connection -- see the constructor's own comment on
        // why this ordering is load-bearing, not stylistic.
        tenantContextBinder.bindNewOrgContext(payload.orgId(), SYSTEM_ACTOR_ID, "SYSTEM", "SYSTEM", Set.of());
        try {
            perOrgTransaction.executeWithoutResult(status ->
                    notificationDispatchService.dispatch(payload.orgId(), payload.recipientUserId(), payload.typeCode(),
                            payload.titleKey(), payload.bodyKey(), payload.params(), payload.entityType(), payload.entityId()));
        } finally {
            tenantContextBinder.clear();
        }
    }

    // recipient_masked exists so message_delivery can be inspected/audited
    // without holding a second, plaintext copy of a phone number/email next
    // to a paid-send billing record -- CLAUDE.md's own "never over-collect"
    // instinct (rule set doesn't name this explicitly for this table, but
    // it mirrors the gov-ID last-4 masking precedent from B-04).
    private static String maskMobile(String mobile) {
        if (mobile == null || mobile.length() < 4) {
            return "***";
        }
        return "*".repeat(mobile.length() - 4) + mobile.substring(mobile.length() - 4);
    }

    private static String maskEmail(String email) {
        if (email == null) {
            return "***";
        }
        int at = email.indexOf('@');
        if (at <= 1) {
            return "***" + email.substring(Math.max(at, 0));
        }
        return email.charAt(0) + "***" + email.substring(at);
    }
}
