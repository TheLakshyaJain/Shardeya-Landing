package com.shardeya.builder.payment.dto;

import com.shardeya.builder.payment.PaymentRecord;
import jakarta.validation.constraints.NotNull;

public record ChequeStatusRequest(@NotNull(message = "error.payment.chequeStatusRequired") PaymentRecord.ChequeStatus status) {
}
