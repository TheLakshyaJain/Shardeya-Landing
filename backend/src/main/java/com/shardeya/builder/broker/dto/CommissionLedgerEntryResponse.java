package com.shardeya.builder.broker.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

/**
 * B-14 §20.5 CommissionLedgerTable row. needsRecovery/recoveryAmount are
 * derived, not stored -- B-14 §10's "recovery entry" for a cancelled deal
 * whose commission was already paid is represented as this same row
 * (status=CANCELLED, amountPaid retained as the real historical transfer,
 * never deleted) rather than a second ledger row, since amountPaid already
 * IS the exact amount that needs recovering and inventing a parallel
 * "negative entry" concept would just be two records answering the same
 * question. See CommissionLedgerService's own comment for the full reasoning.
 */
public record CommissionLedgerEntryResponse(
        UUID id, UUID brokerPartnerId, String brokerName, UUID plotSaleId, String projectName, String plotNumber,
        String buyerName, LocalDate dealDate, BigDecimal dealValue, BigDecimal baseCommission, BigDecimal tierBonus,
        BigDecimal totalCommission, BigDecimal amountPaid, BigDecimal balanceDue, String status,
        boolean needsRecovery, BigDecimal recoveryAmount
) {
}
