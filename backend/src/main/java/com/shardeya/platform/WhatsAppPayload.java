package com.shardeya.platform;

import java.util.UUID;

/**
 * {@link OutboxEvent#getPayload()} shape for {@code event_type = "WHATSAPP"}
 * events. {@code orgId} (M-06 second half) is needed at dispatch time to
 * bind tenant context before the {@code message_delivery} insert -- that
 * table is RLS-protected like every other tenant table.
 */
public record WhatsAppPayload(UUID orgId, String mobile, String text, String purpose) {
}
