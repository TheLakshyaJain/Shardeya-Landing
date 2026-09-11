package com.shardeya.builder.financial.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/** B-08 §14.2 org-wide payment listing row -- joins payment_record to plot_sale/plot/project, unlike PaymentResponse (B-05) which is scoped to a single already-known sale. */
public record FinancialPaymentRow(
        UUID id,
        LocalDate paidOn,
        String projectName,
        String plotNumber,
        String buyerName,
        Short instalmentSequenceNo,
        BigDecimal amount,
        String mode,
        String reference,
        String chequeStatus,
        String recordedByName,
        boolean isReversal,
        boolean dueToChequeBounce,
        String remarks,
        Instant createdAt
) {
}
