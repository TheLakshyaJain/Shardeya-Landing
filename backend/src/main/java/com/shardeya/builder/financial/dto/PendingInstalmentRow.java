package com.shardeya.builder.financial.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

/** B-08 §14.3 -- pending/overdue instalments. daysOverdue is 0 (never negative) for not-yet-due rows, per §7 "negative values suppressed". */
public record PendingInstalmentRow(
        UUID scheduleId,
        UUID plotSaleId,
        String buyerName,
        String buyerMobile,
        String projectName,
        String plotNumber,
        BigDecimal amountDue,
        LocalDate dueDate,
        long daysOverdue,
        String status,
        BigDecimal totalSaleBalance,
        boolean reminderEnabled
) {
}
