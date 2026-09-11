package com.shardeya.builder.tracker.dto;

import java.time.LocalDate;
import java.util.UUID;

/** B-13 §19.1 Follow-up Tracker row. */
public record FollowUpRow(
        UUID customerId,
        LocalDate followUpDate,
        String customerName,
        String customerMobile,
        String projectName,
        String status,
        UUID assignedTo,
        String assignedToName,
        String lastRemark,
        long daysOverdue
) {
}
