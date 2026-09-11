package com.shardeya.foundation.admin.dto;

import com.shardeya.foundation.notification.MessageDelivery;

import java.time.Instant;
import java.util.UUID;

public record MessageDeliveryLogRow(
        UUID id, MessageDelivery.Channel channel, String recipientMasked, String templateCode,
        String provider, MessageDelivery.Status status, String errorCode, Instant sentAt, Instant createdAt
) {
}
