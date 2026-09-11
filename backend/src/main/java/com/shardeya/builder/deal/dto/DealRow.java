package com.shardeya.builder.deal.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

/** B-10 §16 Deals History listing row. brokerName/brokerCommission are always null -- B-14 Broker Management doesn't exist yet. */
public record DealRow(
        UUID saleId,
        LocalDate date,
        String projectName,
        String plotNumber,
        BigDecimal plotSizeSqft,
        String buyerName,
        String buyerMobile,
        BigDecimal dealValue,
        BigDecimal totalCollected,
        BigDecimal balance,
        String brokerName,
        BigDecimal brokerCommission,
        String status,
        String handledByName
) {
}
