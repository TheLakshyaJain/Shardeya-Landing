package com.shardeya.builder.broker.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.UUID;

public record TierOverrideRequest(
        @NotNull(message = "error.brokerTier.tierIdRequired") UUID tierId,
        @NotBlank(message = "error.brokerTier.reasonRequired") @Size(min = 5, max = 500, message = "error.brokerTier.reasonInvalid") String reason
) {
}
