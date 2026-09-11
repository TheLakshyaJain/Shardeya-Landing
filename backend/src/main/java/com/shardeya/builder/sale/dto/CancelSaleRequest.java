package com.shardeya.builder.sale.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * B-04 §10: refund itself is never processed by the platform ("we don't move
 * money"); refundHandling is a free-text note the builder records for their
 * own reference (e.g. "refunded 20L via bank transfer 24 Aug").
 */
public record CancelSaleRequest(
        @NotBlank(message = "error.sale.cancelReasonRequired")
        @Size(min = 5, max = 500, message = "error.sale.cancelReasonInvalid")
        String reason,
        String refundHandling
) {
}
