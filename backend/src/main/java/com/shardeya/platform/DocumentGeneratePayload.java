package com.shardeya.platform;

import java.util.UUID;

/**
 * {@link OutboxEvent#getPayload()} shape for {@code event_type = "DOCUMENT_GENERATE"}
 * events -- B-11 §17.2 "a payment receipt is generated automatically on
 * every payment_record insert via the outbox", CLAUDE.md rule #6 (PDF
 * generation is a side effect, never inline). Only PAYMENT_RECEIPT uses
 * this today (PaymentService.record()); allotment/demand letters are
 * always on-demand per the spec, triggered directly through
 * DocumentController, never via this event.
 */
public record DocumentGeneratePayload(UUID orgId, UUID paymentRecordId) {
}
