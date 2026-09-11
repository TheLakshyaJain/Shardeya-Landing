package com.shardeya.foundation.notification.dto;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

public record NotificationResponse(
        UUID id, String typeCode, String titleKey, String bodyKey, Map<String, Object> params,
        String entityType, UUID entityId, boolean read, Instant createdAt
) {
}
