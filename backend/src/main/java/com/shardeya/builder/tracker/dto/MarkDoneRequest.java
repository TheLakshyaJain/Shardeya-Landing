package com.shardeya.builder.tracker.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record MarkDoneRequest(
        @NotBlank(message = "error.tracker.remarksRequired")
        @Size(min = 1, max = 5000, message = "error.tracker.remarksInvalid")
        String remarks
) {
}
