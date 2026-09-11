package com.shardeya.platform;

import java.util.UUID;

/**
 * {@link OutboxEvent#getPayload()} shape for {@code event_type = "EMAIL"}
 * events. {@code orgId} (M-06 second half) is needed at dispatch time to
 * bind tenant context before the {@code message_delivery} insert.
 */
public record EmailPayload(UUID orgId, String to, String subject, String body) {
}
