package com.shardeya.builder.tracker.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;

public record RescheduleRequest(
        @NotNull(message = "error.tracker.newDateRequired") LocalDate newDate,
        @Size(min = 5, max = 500, message = "error.tracker.reasonInvalid") String reason
) {
}
