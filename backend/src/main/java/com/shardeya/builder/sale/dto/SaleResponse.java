package com.shardeya.builder.sale.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

public record SaleResponse(
        UUID id, UUID plotId, UUID projectId, UUID customerId,
        String buyerName, String buyerMobile, String buyerEmail,
        String buyerGovIdType, String buyerGovIdLast4, UUID buyerGovIdMediaId,
        LocalDate purchaseDate, BigDecimal dealValue,
        UUID brokerPartnerId, String brokerName, String externalBrokerName, String externalBrokerMobile, BigDecimal brokerCommissionAmount,
        String paymentType, String status, Instant cancelledAt, String cancellationReason, UUID handledBy,
        BigDecimal totalPaid, BigDecimal balanceDue,
        // Live-read from whatsapp_optin (keyed by buyerMobile), not a column
        // on plot_sale itself -- see BuyerWhatsAppOptInService.
        boolean buyerWhatsappOptedIn
) {
}
