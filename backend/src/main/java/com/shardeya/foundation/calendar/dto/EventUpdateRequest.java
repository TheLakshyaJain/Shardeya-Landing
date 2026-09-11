package com.shardeya.foundation.calendar.dto;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.UUID;

public record EventUpdateRequest(
        String title,
        LocalDate eventDate,
        LocalTime eventTime,
        Short durationMinutes,
        UUID assignedTo,
        String notes,
        Boolean reminderEnabled
) {
}
