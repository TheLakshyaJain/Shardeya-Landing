package com.shardeya.foundation.customer.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record InteractionAmendRequest(
        @NotBlank(message = "error.interaction.remarksRequired")
        @Size(min = 1, max = 5000, message = "error.interaction.remarksInvalid")
        String remarks
) {
}
