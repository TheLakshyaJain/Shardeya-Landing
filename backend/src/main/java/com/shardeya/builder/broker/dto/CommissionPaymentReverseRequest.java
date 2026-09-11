package com.shardeya.builder.broker.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CommissionPaymentReverseRequest(
        @NotBlank(message = "error.commissionPayment.reasonRequired") @Size(min = 5, max = 500, message = "error.commissionPayment.reasonInvalid") String reason
) {
}
