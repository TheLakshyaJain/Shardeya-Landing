package com.shardeya.builder.payment.dto;

import java.math.BigDecimal;

public record PaymentSummaryResponse(BigDecimal dealValue, BigDecimal totalPaid, BigDecimal totalWaived, BigDecimal balanceDue) {
}
