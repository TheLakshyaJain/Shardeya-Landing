package com.shardeya.builder.broker;

import com.shardeya.builder.broker.CommissionCalculationEngine.ChainMember;
import com.shardeya.builder.broker.CommissionCalculationEngine.CommissionLineItem;
import com.shardeya.builder.broker.CommissionReversalCalculator.BeneficiarySnapshot;
import com.shardeya.builder.broker.CommissionReversalCalculator.ReversalResult;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 06-BROKER-NETWORK-ENGINE.md §9/§41/§8a, build-order step 9 (rewritten
 * for §8a's real payout tracking) -- "reverse each §16 example tree" per
 * step 9's own explicit instruction: every fixture here starts from a REAL
 * frozen tree produced by {@link CommissionCalculationEngine} (the exact
 * same §45/§46 fixtures {@code CommissionCalculationEngineTest} already
 * proved), then simulates a level of released/paid progress and asserts
 * the pure reversal calculation. No DB, no Spring context -- pure function
 * in, pure record out.
 *
 * <p><b>Rewritten, not just re-run, when §8a's real payout tracking
 * landed.</b> Every fixture here previously used {@code paidAmount = 0}
 * (the only value it could ever legitimately hold before §8a, since no
 * payout-recording action existed) and asserted
 * {@code recoveryAmount == releasedAmount} -- which was only ever correct
 * because {@code releasedAmount - 0 == releasedAmount}. That formula
 * ({@code released - paid}) is backwards the moment paidAmount becomes
 * real: {@link CommissionReversalCalculator#reverseOne} now correctly
 * computes recovery as exactly {@code paidAmount} (money that actually
 * left the builder's hand, mirroring M6's own established
 * {@code CommissionLedgerEntryResponse} recovery semantics precisely) --
 * see that method's own javadoc for the full reasoning. Every fixture
 * below was re-derived by hand against the corrected formula, not
 * adjusted until green.
 */
class CommissionReversalCalculatorTest {

    // §45: 200/180/160 chain, 1000 sq.ft. -> B 160k, A 20k, Me 20k.
    private List<CommissionLineItem> the45Tree() {
        UUID b = UUID.randomUUID();
        UUID a = UUID.randomUUID();
        UUID me = UUID.randomUUID();
        List<ChainMember> chain = List.of(
                new ChainMember(b, BigDecimal.valueOf(160), null), // pure differential chain (§45) -- next-slab rate never read
                new ChainMember(a, BigDecimal.valueOf(180), null),
                new ChainMember(me, BigDecimal.valueOf(200), null));
        return CommissionCalculationEngine.calculate(chain, BigDecimal.valueOf(1000));
    }

    // The real reason this class was rewritten: released and paid genuinely
    // differ (75% released, only 25% actually paid out) -- the exact shape
    // that distinguishes the corrected formula (recovery = paid) from the
    // old, now-wrong one (recovery = released - paid, which would have
    // demanded recovering 50% here, more than was ever handed to the
    // broker).
    @Test
    void partiallyPaidTreeRecoversExactlyWhatWasActuallyPaidOutNoMore() {
        List<CommissionLineItem> tree = the45Tree();
        List<BeneficiarySnapshot> snapshots = tree.stream()
                .map(item -> new BeneficiarySnapshot(item.beneficiaryBrokerId(),
                        item.amount(),
                        item.amount().multiply(BigDecimal.valueOf(0.75)).setScale(2, RoundingMode.HALF_UP),
                        item.amount().multiply(BigDecimal.valueOf(0.25)).setScale(2, RoundingMode.HALF_UP)))
                .toList();

        List<ReversalResult> reversed = CommissionReversalCalculator.reverse(snapshots);

        ReversalResult bResult = find(reversed, tree.get(0).beneficiaryBrokerId()); // B, total 160,000
        assertThat(bResult.recoveryAmount()).isEqualByComparingTo(BigDecimal.valueOf(40_000)); // 25% actually paid, NOT 75%-25%=50% (120,000)
        assertThat(bResult.cancelledPendingAmount()).isEqualByComparingTo(BigDecimal.valueOf(120_000)); // everything except what was paid voids

        ReversalResult aResult = find(reversed, tree.get(1).beneficiaryBrokerId()); // A, total 20,000
        assertThat(aResult.recoveryAmount()).isEqualByComparingTo(BigDecimal.valueOf(5_000));
        assertThat(aResult.cancelledPendingAmount()).isEqualByComparingTo(BigDecimal.valueOf(15_000));
    }

    // Released but never paid out: nothing left the builder's hand, so
    // there is nothing to recover -- the whole amount simply voids,
    // regardless of how much had been released.
    @Test
    void releasedButNeverPaidTreeHasNothingToRecoverTheWholeAmountVoids() {
        List<CommissionLineItem> tree = the45Tree();
        List<BeneficiarySnapshot> snapshots = tree.stream()
                .map(item -> new BeneficiarySnapshot(item.beneficiaryBrokerId(),
                        item.amount(),
                        item.amount().multiply(BigDecimal.valueOf(0.25)).setScale(2, RoundingMode.HALF_UP),
                        BigDecimal.ZERO))
                .toList();

        List<ReversalResult> reversed = CommissionReversalCalculator.reverse(snapshots);

        ReversalResult bResult = find(reversed, tree.get(0).beneficiaryBrokerId());
        assertThat(bResult.recoveryAmount()).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(bResult.cancelledPendingAmount()).isEqualByComparingTo(BigDecimal.valueOf(160_000)); // the entire total, not just the never-released 75%
    }

    @Test
    void fullyPaidTreeRecoversTheEntireFrozenAmountWithNothingLeftPending() {
        List<CommissionLineItem> tree = the45Tree();
        List<BeneficiarySnapshot> snapshots = tree.stream()
                .map(item -> new BeneficiarySnapshot(item.beneficiaryBrokerId(), item.amount(), item.amount(), item.amount()))
                .toList();

        List<ReversalResult> reversed = CommissionReversalCalculator.reverse(snapshots);

        ReversalResult bResult = find(reversed, tree.get(0).beneficiaryBrokerId());
        assertThat(bResult.recoveryAmount()).isEqualByComparingTo(BigDecimal.valueOf(160_000)); // the whole thing was paid out
        assertThat(bResult.cancelledPendingAmount()).isEqualByComparingTo(BigDecimal.ZERO); // nothing left unpaid

        ReversalResult meResult = find(reversed, tree.get(2).beneficiaryBrokerId());
        assertThat(meResult.recoveryAmount()).isEqualByComparingTo(BigDecimal.valueOf(20_000));
        assertThat(meResult.cancelledPendingAmount()).isEqualByComparingTo(BigDecimal.ZERO);
    }

    @Test
    void neverReleasedTreeHasNothingToRecoverButTheFullAmountVoids() {
        List<CommissionLineItem> tree = the45Tree();
        List<BeneficiarySnapshot> snapshots = tree.stream()
                .map(item -> new BeneficiarySnapshot(item.beneficiaryBrokerId(), item.amount(), BigDecimal.ZERO, BigDecimal.ZERO))
                .toList();

        List<ReversalResult> reversed = CommissionReversalCalculator.reverse(snapshots);

        for (ReversalResult r : reversed) {
            assertThat(r.recoveryAmount()).isEqualByComparingTo(BigDecimal.ZERO);
        }
        ReversalResult bResult = find(reversed, tree.get(0).beneficiaryBrokerId());
        assertThat(bResult.cancelledPendingAmount()).isEqualByComparingTo(BigDecimal.valueOf(160_000));
    }

    // A zero-amount UPLINE_DIFFERENTIAL row (§33: lower-rated upline) never
    // has anything released or paid against it either -- the reversal of a
    // genuine zero must still cleanly be zero, not throw or go negative.
    @Test
    void aZeroAmountLineItemReversesToZeroCleanly() {
        BeneficiarySnapshot zero = new BeneficiarySnapshot(UUID.randomUUID(), BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO);
        ReversalResult result = CommissionReversalCalculator.reverseOne(zero);
        assertThat(result.recoveryAmount()).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(result.cancelledPendingAmount()).isEqualByComparingTo(BigDecimal.ZERO);
    }

    // Defensive-only: paidAmount can never legitimately be negative or
    // exceed totalAmount in this codebase (BrokerCommissionPaymentService's
    // own hard cap prevents paying out more than has been released, which
    // is itself capped at totalAmount), but the pure function itself must
    // never report a negative recovery or a negative cancelledPendingAmount
    // if it somehow did.
    @Test
    void recoveryAndVoidedAmountNeverGoNegativeEvenIfPaidIsImpossible() {
        BeneficiarySnapshot negativePaid = new BeneficiarySnapshot(UUID.randomUUID(),
                BigDecimal.valueOf(100), BigDecimal.valueOf(40), BigDecimal.valueOf(-10));
        assertThat(CommissionReversalCalculator.reverseOne(negativePaid).recoveryAmount()).isEqualByComparingTo(BigDecimal.ZERO);

        BeneficiarySnapshot paidExceedsTotal = new BeneficiarySnapshot(UUID.randomUUID(),
                BigDecimal.valueOf(100), BigDecimal.valueOf(100), BigDecimal.valueOf(150));
        assertThat(CommissionReversalCalculator.reverseOne(paidExceedsTotal).cancelledPendingAmount()).isEqualByComparingTo(BigDecimal.ZERO);
    }

    private ReversalResult find(List<ReversalResult> results, UUID beneficiaryId) {
        return results.stream().filter(r -> r.beneficiaryBrokerId().equals(beneficiaryId)).findFirst().orElseThrow();
    }
}
