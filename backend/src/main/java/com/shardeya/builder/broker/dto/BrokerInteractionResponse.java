package com.shardeya.builder.broker.dto;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

public record BrokerInteractionResponse(
        UUID id, LocalDate occurredOn, String type, String remarks, LocalDate nextFollowUpDate,
        UUID conductedBy, Instant createdAt
) {
}
