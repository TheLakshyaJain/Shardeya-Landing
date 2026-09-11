package com.shardeya.builder.broker.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

/** B-14 §4 "POST /brokers/{id}/commission-preview {projectId, plotId, dealValue} -> resolved commission". saleDate defaults to today when omitted (a preview while drafting a sale, before purchaseDate is finalised). */
public record CommissionPreviewRequest(
        UUID projectId,
        UUID plotId,
        @NotNull(message = "error.commissionConfig.dealValueRequired") @Positive(message = "error.commissionConfig.dealValueInvalid") BigDecimal dealValue,
        LocalDate saleDate
) {
}
