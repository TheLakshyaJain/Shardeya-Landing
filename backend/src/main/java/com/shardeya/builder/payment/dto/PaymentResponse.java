package com.shardeya.builder.payment.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public record PaymentResponse(
        UUID id, UUID plotSaleId, String receiptNo, BigDecimal amount, LocalDate paidOn, String mode,
        String reference, String chequeStatus, UUID receivedBy, String remarks, UUID reversesPaymentId,
        List<AllocationResponse> allocations, Instant createdAt
) {
    public record AllocationResponse(UUID scheduleId, BigDecimal amount) {
    }
}
