package com.shardeya.builder.broker.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * 06-BROKER-NETWORK-ENGINE.md §7/§8/§8a/§9 -- a DESIGNATION broker's own
 * commission-tree row, one per booking they're a beneficiary of (as
 * seller or upline). The Ledger-tab-equivalent read surface for this
 * broker type; total/released/paid mirror booking_commission's own
 * column shape directly.
 *
 * <p><b>The revisit this class's own javadoc once asked for, now done:</b>
 * before §8a, paidAmount had no write path anywhere in this codebase
 * (always 0), so outstandingAmount (totalAmount - releasedAmount, "not yet
 * released at all") was deliberately used instead of the entity's own
 * pending_amount column (released_amount - paid_amount) to avoid a
 * "Pending" figure that would always equal "Released" on screen. Now that
 * §8a wires a real payout path, pending_amount finally means something
 * real -- exposed here as {@code dueAmount} (Commission Due =
 * Released - Paid, the number the builder acts on; see
 * BrokerCommissionPaymentService's own class javadoc for the full
 * Earned/Released/Paid/Due model). outstandingAmount is kept, unchanged,
 * for the still-separately-useful "how much more will release as the
 * customer pays more" question -- the two figures answer genuinely
 * different questions and neither replaces the other.
 *
 * <p>needsRecovery/recoveryAmount are derived at read time via
 * CommissionReversalCalculator, never stored -- the exact same
 * "status=CANCELLED plus the existing released amount IS the recovery
 * fact" pattern CommissionLedgerEntryResponse already established for
 * PERCENTAGE brokers (step 9's own explicit instruction to reuse it) --
 * corrected for §8a to gate on paidAmount, not releasedAmount (see
 * CommissionReversalCalculator's own javadoc for why the old
 * released-based formula was wrong the moment paidAmount became real).
 */
public record BookingCommissionResponse(
        UUID id, UUID plotSaleId, String projectName, String plotNumber, String buyerName,
        UUID sellingBrokerId, String sellingBrokerName, int uplineLevel, String commissionType,
        BigDecimal plotAreaSqft, BigDecimal commissionPerSqft, BigDecimal totalAmount,
        BigDecimal releasedAmount, BigDecimal paidAmount, BigDecimal dueAmount, BigDecimal outstandingAmount,
        String status, Instant createdAt, boolean needsRecovery, BigDecimal recoveryAmount
) {
}
