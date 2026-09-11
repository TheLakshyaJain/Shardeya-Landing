package com.shardeya.builder.payment.dto;

import com.shardeya.builder.payment.PaymentRecord;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/** Field validations mirror 03-BUILDER-MODULES.md B-05 §11. */
public record PaymentCreateRequest(
        @NotNull(message = "error.payment.amountRequired") BigDecimal amount,
        @NotNull(message = "error.payment.paidOnRequired") LocalDate paidOn,
        @NotNull(message = "error.payment.modeRequired") PaymentRecord.Mode mode,
        @Size(max = 120, message = "error.payment.referenceInvalid") String reference,
        String remarks,
        // Manual override -- omitted/empty means auto-allocate oldest-due-first.
        List<@Valid AllocationRequest> allocations
) {
}
