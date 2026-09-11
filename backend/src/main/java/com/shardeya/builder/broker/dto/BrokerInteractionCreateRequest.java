package com.shardeya.builder.broker.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;

public record BrokerInteractionCreateRequest(
        @NotNull(message = "error.brokerInteraction.occurredOnRequired") LocalDate occurredOn,
        @Size(max = 20, message = "error.brokerInteraction.typeInvalid") String type,
        @NotBlank(message = "error.brokerInteraction.remarksRequired") String remarks,
        LocalDate nextFollowUpDate
) {
}
