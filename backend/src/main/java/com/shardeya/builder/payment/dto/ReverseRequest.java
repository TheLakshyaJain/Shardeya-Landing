package com.shardeya.builder.payment.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record ReverseRequest(
        @NotBlank(message = "error.payment.reverseReasonRequired")
        @Size(min = 5, max = 500, message = "error.payment.reverseReasonInvalid")
        String reason
) {
}
