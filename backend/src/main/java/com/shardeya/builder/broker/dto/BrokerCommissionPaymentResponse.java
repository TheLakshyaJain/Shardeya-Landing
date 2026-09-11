package com.shardeya.builder.broker.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

public record BrokerCommissionPaymentResponse(
        UUID id, UUID beneficiaryBrokerId, BigDecimal amount, LocalDate paidOn, String mode,
        String reference, String remarks, UUID reversesPaymentId, Instant createdAt
) {
}
