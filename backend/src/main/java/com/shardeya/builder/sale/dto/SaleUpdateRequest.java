package com.shardeya.builder.sale.dto;

import java.math.BigDecimal;

/** PATCH /sales/{id} -- buyer/deal detail edits. Every field optional (only supplied ones change). */
public record SaleUpdateRequest(
        String buyerName,
        String buyerMobile,
        String buyerEmail,
        BigDecimal dealValue,
        String externalBrokerName,
        String externalBrokerMobile,
        BigDecimal brokerCommissionAmount
) {
}
