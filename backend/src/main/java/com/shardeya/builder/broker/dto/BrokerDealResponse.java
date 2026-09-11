package com.shardeya.builder.broker.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

/** B-14 §20.6 BrokerDealsTable row. commissionStatus is null when the sale predates a resolvable ledger entry (shouldn't happen post-M6, but the join is left-outer in spirit). */
public record BrokerDealResponse(
        UUID saleId, String projectName, String plotNumber, String buyerName, LocalDate purchaseDate,
        BigDecimal dealValue, String saleStatus, BigDecimal commissionEarned, String commissionStatus
) {
}
