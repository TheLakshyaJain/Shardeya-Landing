package com.shardeya.builder.payment.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import java.math.BigDecimal;
import java.util.UUID;

public record AllocationRequest(
        @NotNull(message = "error.payment.scheduleIdRequired") UUID scheduleId,
        @NotNull(message = "error.payment.allocationAmountRequired") @Positive(message = "error.payment.allocationAmountInvalid") BigDecimal amount
) {
}
