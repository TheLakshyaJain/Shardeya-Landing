package com.shardeya.builder.payment.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

public record ScheduleResponse(
        UUID id, int sequenceNo, String label, BigDecimal expectedAmount, LocalDate dueDate,
        String status, BigDecimal amountAllocated, boolean reminderEnabled, long daysOverdue
) {
}
