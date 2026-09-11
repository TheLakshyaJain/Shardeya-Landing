package com.shardeya.builder.broker.dto;

import com.shardeya.builder.broker.CommissionPayment;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;
import java.time.LocalDate;

/** B-14 §4 "POST /commission-ledger/{id}/payments {amount, paidOn, mode, reference, remarks}". */
public record CommissionPaymentCreateRequest(
        @NotNull(message = "error.commissionPayment.amountRequired") @DecimalMin(value = "0.01", message = "error.commissionPayment.amountInvalid") BigDecimal amount,
        @NotNull(message = "error.commissionPayment.paidOnRequired") LocalDate paidOn,
        @NotNull(message = "error.commissionPayment.modeRequired") CommissionPayment.Mode mode,
        String reference,
        String remarks,
        // B-14 §11: "commission payment amount > 0, <= balance_due
        // (overpayment requires confirmation)" -- the frontend shows a
        // confirmation dialog first, then resubmits with this set true.
        boolean confirmOverpayment
) {
}
