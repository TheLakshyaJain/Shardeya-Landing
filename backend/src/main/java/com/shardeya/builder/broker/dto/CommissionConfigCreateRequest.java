package com.shardeya.builder.broker.dto;

import com.shardeya.builder.broker.BrokerCommissionConfig;
import com.shardeya.builder.broker.BrokerPartner;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;

import java.time.LocalDate;
import java.util.UUID;

/** B-14 §4 "POST /brokers/{id}/commission-configs {scope, projectId?, plotId?, type, rate, effectiveFrom}". */
public record CommissionConfigCreateRequest(
        @NotNull(message = "error.commissionConfig.scopeRequired") BrokerCommissionConfig.Scope scope,
        UUID projectId,
        UUID plotId,
        @NotNull(message = "error.commissionConfig.typeRequired") BrokerPartner.CommissionType commissionType,
        @NotNull(message = "error.commissionConfig.rateRequired") @DecimalMin(value = "0.001", message = "error.commissionConfig.rateInvalid") java.math.BigDecimal rateValue,
        @NotNull(message = "error.commissionConfig.effectiveFromRequired") LocalDate effectiveFrom,
        LocalDate effectiveTo
) {
}
