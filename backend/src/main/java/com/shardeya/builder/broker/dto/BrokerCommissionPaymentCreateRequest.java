package com.shardeya.builder.broker.dto;

import com.shardeya.builder.broker.BrokerCommissionPayment;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * 06-BROKER-NETWORK-ENGINE.md §8a "POST /brokers/{id}/commission-payments" --
 * pays against the broker's WHOLE Due balance (Released - Paid across
 * every non-cancelled booking_commission row they're a beneficiary of),
 * auto-allocated oldest-first by BrokerCommissionPaymentService. No target
 * entry id here (unlike CommissionPaymentCreateRequest, which pays one
 * specific commission_ledger_entry) and deliberately NO confirmOverpayment
 * escape hatch -- §8a's hard cap is a flat reject, not a confirm-to-proceed
 * flow, since paying past Due would mean paying against money not yet
 * collected from the customer.
 */
public record BrokerCommissionPaymentCreateRequest(
        @NotNull(message = "error.brokerCommissionPayment.amountRequired")
        @DecimalMin(value = "0.01", message = "error.brokerCommissionPayment.amountInvalid")
        BigDecimal amount,
        @NotNull(message = "error.brokerCommissionPayment.paidOnRequired") LocalDate paidOn,
        @NotNull(message = "error.brokerCommissionPayment.modeRequired") BrokerCommissionPayment.Mode mode,
        String reference,
        String remarks
) {
}
