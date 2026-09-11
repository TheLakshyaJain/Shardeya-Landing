package com.shardeya.foundation.customer.dto;

import com.shardeya.foundation.customer.Interaction;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;
import java.util.UUID;

/** Field validations mirror 02-FOUNDATION-MODULES.md M-12 §11 (interaction.remarks/occurred_on). */
public record InteractionCreateRequest(
        @NotNull(message = "error.interaction.occurredOnRequired") LocalDate occurredOn,
        @NotNull(message = "error.interaction.typeRequired") Interaction.Type type,
        @NotBlank(message = "error.interaction.remarksRequired")
        @Size(min = 1, max = 5000, message = "error.interaction.remarksInvalid")
        String remarks,
        LocalDate nextFollowUpDate,
        Interaction.Result result,
        UUID propertyId,
        UUID projectId,
        UUID plotId
) {
}
