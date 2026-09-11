package com.shardeya.builder.broker;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

/**
 * 06-BROKER-NETWORK-ENGINE.md §9/§41, build-order step 9 -- a pure,
 * dependency-free function computing the recovery view of a frozen
 * commission tree at cancellation time, mirrored from
 * {@link CommissionCalculationEngine}'s own zero-dependency style (no
 * Spring, no DB, no service calls) and built + fixture-tested BEFORE any
 * wiring, per this step's own explicit instruction.
 *
 * <p>Reuses the exact M6 recovery semantics {@code CommissionLedgerService}
 * already established for PERCENTAGE brokers ("amount_paid on a CANCELLED
 * entry is exactly how much needs recovering", derived at read time, never
 * a second reversal row) -- {@link #reverse} never mutates anything, it
 * just computes what recovery/void figures a snapshot of (total, released,
 * paid) implies.
 *
 * <p><b>Corrected for §8a, real payout tracking.</b> Before §8a,
 * {@code paidAmount} had no write path anywhere in this codebase (always
 * 0), and this method computed {@code recovery = releasedAmount -
 * paidAmount} as a stand-in -- reasonable only because that formula was
 * coincidentally identical to {@code releasedAmount} whenever
 * {@code paidAmount} was always 0. That formula is backwards the moment a
 * real payout exists: recovery must mean "money that actually left the
 * builder's hand," i.e. exactly {@code paidAmount} -- not
 * {@code released - paid}, which would (wrongly) demand recovering MORE
 * than was ever paid out whenever released exceeds paid (the ordinary
 * case for any partially-paid-out booking). A released-but-never-paid
 * slice never left the builder's hand at all, so cancelling it recovers
 * nothing for that slice -- it simply stops being owed, exactly like a
 * never-released slice. See {@code CommissionReversalCalculatorTest}'s own
 * class javadoc for how this was found (re-deriving every fixture by hand
 * once paidAmount could finally be non-zero, not discovered as a runtime
 * bug) and proven (the old formula's fixtures were confirmed to fail
 * against the corrected code before the fix, and pass after).
 */
public final class CommissionReversalCalculator {

    /** A frozen tree line item's state at the moment of cancellation. */
    public record BeneficiarySnapshot(UUID beneficiaryBrokerId, BigDecimal totalAmount, BigDecimal releasedAmount, BigDecimal paidAmount) {
    }

    /**
     * @param recoveryAmount exactly how much was actually paid out to this
     *                       beneficiary before cancellation -- the real
     *                       debt the builder is now owed back. Never
     *                       negative.
     * @param cancelledPendingAmount everything else the frozen total would
     *                               have covered (whether never released,
     *                               or released but never paid out) --
     *                               simply voids, since none of it ever
     *                               left the builder's hand. Never
     *                               negative.
     */
    public record ReversalResult(UUID beneficiaryBrokerId, BigDecimal recoveryAmount, BigDecimal cancelledPendingAmount) {
    }

    private CommissionReversalCalculator() {
    }

    public static List<ReversalResult> reverse(List<BeneficiarySnapshot> tree) {
        return tree.stream().map(CommissionReversalCalculator::reverseOne).toList();
    }

    public static ReversalResult reverseOne(BeneficiarySnapshot b) {
        BigDecimal recovery = b.paidAmount();
        if (recovery.signum() < 0) {
            // Defensive only -- paidAmount can never legitimately be
            // negative in this codebase, but a reversal calculation must
            // never report a negative "amount owed back."
            recovery = BigDecimal.ZERO;
        }
        BigDecimal cancelledPending = b.totalAmount().subtract(b.paidAmount());
        if (cancelledPending.signum() < 0) {
            // Defensive only -- paidAmount can never legitimately exceed
            // totalAmount (BrokerCommissionPaymentService's own hard cap
            // prevents it), but this must never report a negative void.
            cancelledPending = BigDecimal.ZERO;
        }
        return new ReversalResult(b.beneficiaryBrokerId(), recovery, cancelledPending);
    }
}
