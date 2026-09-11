package com.shardeya.builder.broker.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record BrokerCommissionPaymentReverseRequest(
        @NotBlank(message = "error.brokerCommissionPayment.reasonRequired")
        @Size(min = 5, max = 500, message = "error.brokerCommissionPayment.reasonInvalid")
        String reason
) {
}
