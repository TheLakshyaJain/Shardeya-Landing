package com.shardeya.builder.tracker.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

/** B-13 §19.2 Collection Tracker row. */
public record CollectionRow(
        UUID scheduleId,
        UUID plotSaleId,
        LocalDate dueDate,
        String projectName,
        String plotNumber,
        String buyerName,
        String buyerMobile,
        BigDecimal amountDue,
        long daysOverdue,
        BigDecimal totalBalance,
        boolean reminderEnabled,
        // Whether the buyer has an active WhatsApp opt-in on file -- the
        // frontend uses this to disable "Send Reminder" proactively with a
        // clear explanation, rather than letting the click fail silently
        // against TrackerService's own server-side gate.
        boolean buyerOptedIn
) {
}
