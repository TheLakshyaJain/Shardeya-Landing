package com.shardeya.builder.payment.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record WaiveRequest(
        @NotBlank(message = "error.schedule.waiveReasonRequired")
        @Size(min = 5, max = 500, message = "error.schedule.waiveReasonInvalid")
        String reason
) {
}
