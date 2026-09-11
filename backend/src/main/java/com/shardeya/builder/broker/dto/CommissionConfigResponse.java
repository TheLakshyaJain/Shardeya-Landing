package com.shardeya.builder.broker.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

public record CommissionConfigResponse(
        UUID id, UUID brokerPartnerId, String scope, UUID projectId, String projectName, UUID plotId, String plotNumber,
        String commissionType, BigDecimal rateValue, LocalDate effectiveFrom, LocalDate effectiveTo
) {
}
