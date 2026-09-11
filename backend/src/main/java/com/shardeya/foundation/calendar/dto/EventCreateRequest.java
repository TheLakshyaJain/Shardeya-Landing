package com.shardeya.foundation.calendar.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.UUID;

/** Field validations mirror 02-FOUNDATION-MODULES.md M-11 §11. Always a MANUAL_MEETING/IMPORTANT_DATE event -- auto events are never created through this endpoint. */
public record EventCreateRequest(
        @NotBlank(message = "error.calendar.titleRequired")
        @Size(min = 2, max = 150, message = "error.calendar.titleInvalid")
        String title,

        @NotNull(message = "error.calendar.dateRequired")
        LocalDate eventDate,

        LocalTime eventTime,
        Short durationMinutes,
        boolean importantDate,
        UUID customerId,
        UUID propertyId,
        UUID projectId,
        UUID plotId,
        UUID assignedTo,
        String notes,
        boolean reminderEnabled
) {
}
