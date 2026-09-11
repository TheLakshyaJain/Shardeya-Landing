package com.shardeya.foundation.calendar.dto;

import com.shardeya.foundation.calendar.CalendarEvent;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.UUID;

public record CalendarEventResponse(
        UUID id,
        String title,
        LocalDate eventDate,
        LocalTime eventTime,
        Short durationMinutes,
        CalendarEvent.EventType eventType,
        CalendarEvent.Source source,
        UUID customerId,
        UUID propertyId,
        UUID projectId,
        UUID plotId,
        UUID assignedTo,
        String notes,
        boolean reminderEnabled,
        CalendarEvent.Status status,
        Instant createdAt
) {
    public static CalendarEventResponse from(CalendarEvent e) {
        return new CalendarEventResponse(e.getId(), e.getTitle(), e.getEventDate(), e.getEventTime(),
                e.getDurationMinutes(), e.getEventType(), e.getSource(), e.getCustomerId(), e.getPropertyId(),
                e.getProjectId(), e.getPlotId(), e.getAssignedTo(), e.getNotes(), e.isReminderEnabled(),
                e.getStatus(), e.getCreatedAt());
    }
}
