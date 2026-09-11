package com.shardeya.builder.deal.dto;

import com.shardeya.builder.payment.dto.PaymentResponse;
import com.shardeya.builder.sale.dto.PlotDocumentResponse;
import com.shardeya.foundation.customer.dto.InteractionResponse;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * B-10 §7 "Detail view assembles buyer info, plot/project info, the full
 * payment history with receipts, all documents, the lead's interaction
 * timeline, commission ledger entry, and every audit event." Commission
 * ledger is always empty -- B-14 doesn't exist yet, same documented
 * boundary as every other milestone since M3's plot_sale.broker_partner_id.
 */
public record DealDetailResponse(
        UUID saleId,
        LocalDate purchaseDate,
        String status,
        String projectName,
        String plotNumber,
        BigDecimal plotSizeSqft,
        String buyerName,
        String buyerMobile,
        String buyerEmail,
        BigDecimal dealValue,
        BigDecimal totalCollected,
        BigDecimal balance,
        String cancellationReason,
        String handledByName,
        boolean handledByFormerStaff,
        List<PaymentResponse> payments,
        List<PlotDocumentResponse> documents,
        List<InteractionResponse> interactionTimeline,
        List<Object> commissionLedger
) {
}
