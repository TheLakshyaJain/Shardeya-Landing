package com.shardeya.builder.broker;

import com.shardeya.builder.broker.CommissionCalculationEngine.ChainMember;
import com.shardeya.builder.broker.CommissionCalculationEngine.CommissionLineItem;
import com.shardeya.builder.broker.CommissionCalculationEngine.CommissionType;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 06-BROKER-NETWORK-ENGINE.md §16 -- every fixture listed there, verified
 * against the pure {@link CommissionCalculationEngine}, no DB, no Spring
 * context. Per the milestone's own explicit instruction, this suite must
 * be fully green BEFORE any wiring into PlotSaleService (build-order step
 * 5, out of scope for this round).
 *
 * <p>§16 gives exact expected totals only for §45 and §46; the other
 * fixtures (§21, §17, §33) are described by their chain structure only.
 * Their expected values below are derived directly from §7's own stated
 * algorithm (shown worked out in each test's comment) -- not invented
 * independently of the spec.
 *
 * <p><b>Rewritten, not just re-run, for §7's revised same-slab formula.</b>
 * Every fixture here previously used a flat ₹10/sq.ft. same-slab bonus
 * regardless of which slab a pair shared. That formula is now
 * {@code nextSlabRate(U) - rate(U)} -- see
 * {@link CommissionCalculationEngine#calculate}'s own javadoc for the
 * full before/after. Fixtures that never trigger the same-slab branch at
 * all (pure differential chains: §45, §17, §33, the top-level and
 * fractional-area cases) are numerically UNCHANGED, only their
 * {@code ChainMember} construction updated for the new
 * {@code nextSlabRatePerSqft} field. Fixtures that DO trigger it (§46,
 * §21) were re-derived by hand against the real lookup table below, not
 * adjusted until green -- §46 in particular is a case where the new
 * formula happens to give the SAME ₹10k as the old flat value (215's own
 * next-minus-current is coincidentally ₹10), which is exactly why the
 * spec's own note asks for a ₹200 case too (this class's own
 * {@code section_sameSlabAt200...} test) to prove the formula really
 * varies and isn't secretly still hardcoded at ₹10.
 *
 * <p>Same-slab lookup table (rate -&gt; next-slab rate -&gt; incentive),
 * matching 06-BROKER-NETWORK-ENGINE.md §7's own table exactly:
 * <pre>
 * 160 -&gt; 180 -&gt; 20      215 -&gt; 225 -&gt; 10
 * 180 -&gt; 200 -&gt; 20      225 -&gt; 235 -&gt; 10
 * 200 -&gt; 215 -&gt; 15      235 -&gt; 245 -&gt; 10
 * 245 -&gt; 255 -&gt; 10      255 -&gt; (none) -&gt; 0
 * </pre>
 */
class CommissionCalculationEngineTest {

    /** A pure-differential-role member -- its own next-slab rate is irrelevant here (never read unless this member later turns out to be a same-slab upline), so null is honest, not a shortcut. */
    private static ChainMember member(BigDecimal rate) {
        return new ChainMember(UUID.randomUUID(), rate, null);
    }

    /** A member whose own next-slab-up rate matters -- used whenever this member is the UPLINE in a same-slab pairing. Matches this class's own lookup table above. */
    private static ChainMember sameSlabMember(BigDecimal rate, BigDecimal nextSlabRate) {
        return new ChainMember(UUID.randomUUID(), rate, nextSlabRate);
    }

    private static BigDecimal rate(int value) {
        return BigDecimal.valueOf(value).setScale(2);
    }

    /**
     * §45: chain bottom-to-top 160/180/200 (seller B=160, A=180, Me=200),
     * 1000 sq.ft. -> B ₹160k [SELLING_BROKER], A ₹20k [UPLINE_DIFFERENTIAL],
     * Me ₹20k [UPLINE_DIFFERENTIAL]. Exact figures given by the spec. Pure
     * differential chain -- never triggers the same-slab branch, so this
     * fixture is numerically unaffected by the formula change.
     */
    @Test
    void section45_differentialChain() {
        ChainMember b = member(rate(160));
        ChainMember a = member(rate(180));
        ChainMember me = member(rate(200));
        List<CommissionLineItem> result = CommissionCalculationEngine.calculate(List.of(b, a, me), BigDecimal.valueOf(1000));

        assertThat(result).hasSize(3);
        assertLine(result.get(0), b.brokerId(), 0, CommissionType.SELLING_BROKER, "160000.00");
        assertLine(result.get(1), a.brokerId(), 1, CommissionType.UPLINE_DIFFERENTIAL, "20000.00");
        assertLine(result.get(2), me.brokerId(), 2, CommissionType.UPLINE_DIFFERENTIAL, "20000.00");
        assertThat(totalOf(result)).isEqualByComparingTo("200000.00");
    }

    /**
     * §46: chain 215/215/215, 1000 sq.ft. -> B ₹215k [SELLING_BROKER], A and
     * Me each get (nextSlabRate(215) − 215) = (225 − 215) = ₹10/sq.ft. ->
     * ₹10k, TOTAL ₹235k -- explicitly NOT capped to seller's own ₹215k
     * (§7/§13/§15/§20's own repeated emphasis). Numerically identical to
     * the OLD flat-₹10 formula's answer at exactly this slab -- a genuine
     * coincidence (215's own next-minus-current happens to equal 10), not
     * evidence the formula still hardcodes ₹10 -- see
     * {@link #section_sameSlabAt200RatePairGivesFifteenNotTen} for the
     * fixture that actually proves it varies.
     */
    @Test
    void section46_sameSlabBonusNotCapped() {
        ChainMember b = member(rate(215));
        ChainMember a = sameSlabMember(rate(215), rate(225));
        ChainMember me = sameSlabMember(rate(215), rate(225));
        List<CommissionLineItem> result = CommissionCalculationEngine.calculate(List.of(b, a, me), BigDecimal.valueOf(1000));

        assertThat(result).hasSize(3);
        assertLine(result.get(0), b.brokerId(), 0, CommissionType.SELLING_BROKER, "215000.00");
        assertLine(result.get(1), a.brokerId(), 1, CommissionType.NETWORK_SAME_SLAB_BONUS, "10000.00");
        assertLine(result.get(2), me.brokerId(), 2, CommissionType.NETWORK_SAME_SLAB_BONUS, "10000.00");

        BigDecimal total = totalOf(result);
        assertThat(total).isEqualByComparingTo("235000.00");
        // The actual "not capped" assertion: total strictly exceeds what
        // capping to the seller's own rate (215 x 1000 = 215000) would give.
        assertThat(total).isGreaterThan(new BigDecimal("215000.00"));
    }

    /**
     * NEW fixture (06-BROKER-NETWORK-ENGINE.md §7's own explicit ask): both
     * on ₹200, 1000 sq.ft. -> same-slab incentive = (215 − 200) = ₹15/sq.ft.,
     * NOT ₹10. This is the fixture that actually distinguishes the new
     * formula from the old flat value -- ₹46 alone would pass even if the
     * code still secretly hardcoded ₹10, since 215's own answer happens to
     * be 10 either way.
     */
    @Test
    void section_sameSlabAt200RatePairGivesFifteenNotTen() {
        ChainMember b = member(rate(200));
        ChainMember a = sameSlabMember(rate(200), rate(215));
        List<CommissionLineItem> result = CommissionCalculationEngine.calculate(List.of(b, a), BigDecimal.valueOf(1000));

        assertThat(result).hasSize(2);
        assertLine(result.get(0), b.brokerId(), 0, CommissionType.SELLING_BROKER, "200000.00");
        assertLine(result.get(1), a.brokerId(), 1, CommissionType.NETWORK_SAME_SLAB_BONUS, "15000.00");
        assertThat(totalOf(result)).isEqualByComparingTo("215000.00");
    }

    /** NEW fixture: both on ₹160 (the bottom slab), 1000 sq.ft. -> same-slab incentive = (180 − 160) = ₹20/sq.ft., the largest incentive on the whole ladder. */
    @Test
    void section_sameSlabAt160RatePairGivesTwenty() {
        ChainMember b = member(rate(160));
        ChainMember a = sameSlabMember(rate(160), rate(180));
        List<CommissionLineItem> result = CommissionCalculationEngine.calculate(List.of(b, a), BigDecimal.valueOf(1000));

        assertThat(result).hasSize(2);
        assertLine(result.get(0), b.brokerId(), 0, CommissionType.SELLING_BROKER, "160000.00");
        assertLine(result.get(1), a.brokerId(), 1, CommissionType.NETWORK_SAME_SLAB_BONUS, "20000.00");
        assertThat(totalOf(result)).isEqualByComparingTo("180000.00");
    }

    /**
     * NEW fixture, the mandatory top-slab edge case: both on ₹255 (the top
     * slab, President) -- there is no slab above it, so
     * {@code nextSlabRatePerSqft} is {@code null} and the incentive must be
     * an explicit ₹0. Never a fabricated higher rate, never a negative
     * number, and never a silent fallback to the old flat ₹10.
     */
    @Test
    void section_sameSlabAtTopSlab255GivesExactlyZeroNeverTenNeverNegative() {
        ChainMember b = member(rate(255));
        ChainMember a = sameSlabMember(rate(255), null); // no slab above the top one
        List<CommissionLineItem> result = CommissionCalculationEngine.calculate(List.of(b, a), BigDecimal.valueOf(1000));

        assertThat(result).hasSize(2);
        assertLine(result.get(0), b.brokerId(), 0, CommissionType.SELLING_BROKER, "255000.00");
        CommissionLineItem uplineRow = result.get(1);
        assertThat(uplineRow.type()).isEqualTo(CommissionType.NETWORK_SAME_SLAB_BONUS);
        assertThat(uplineRow.amount()).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(uplineRow.amount().signum()).isGreaterThanOrEqualTo(0); // explicitly never negative
        assertThat(totalOf(result)).isEqualByComparingTo("255000.00"); // A contributes nothing
    }

    /**
     * §21: five-level mixed chain, bottom-to-top 160/200/200/215/215
     * (matching the spec's own top-to-bottom listing "215/215/200/200/160"
     * reversed), 1000 sq.ft. -- the multi-level fixture with same-slab
     * appearing at TWO DIFFERENT slabs (200 and 215), each computing its
     * own independent next-minus-current. Derived from §7's algorithm:
     * <pre>
     * B(160):        1000 x 160 = 160000                        [SELLING_BROKER]
     * L1(200) vs B(160):   200&gt;160 -&gt; 1000x40 = 40000                [UPLINE_DIFFERENTIAL]
     * L2(200) vs L1(200):  200==200 -&gt; 1000x(215-200) = 15000        [NETWORK_SAME_SLAB_BONUS] (was 10000 under the old flat formula)
     * L3(215) vs L2(200):  215&gt;200 -&gt; 1000x15 = 15000                [UPLINE_DIFFERENTIAL]
     * L4(215) vs L3(215):  215==215 -&gt; 1000x(225-215) = 10000        [NETWORK_SAME_SLAB_BONUS] (unchanged, 215's own coincidence again)
     * total = 240000 (was 235000 under the old flat formula)
     * </pre>
     * This is also the key regression guard for "compare with direct
     * downline only, never the seller": comparing L3/L4 against the
     * seller's 160 instead of their real direct downlines would produce
     * completely different (larger) numbers than asserted here.
     */
    @Test
    void section21_fiveLevelMixedDifferentialAndSameSlab() {
        ChainMember b = member(rate(160));
        ChainMember l1 = member(rate(200));
        ChainMember l2 = sameSlabMember(rate(200), rate(215));
        ChainMember l3 = member(rate(215));
        ChainMember l4 = sameSlabMember(rate(215), rate(225));
        List<CommissionLineItem> result = CommissionCalculationEngine.calculate(List.of(b, l1, l2, l3, l4), BigDecimal.valueOf(1000));

        assertThat(result).hasSize(5);
        assertLine(result.get(0), b.brokerId(), 0, CommissionType.SELLING_BROKER, "160000.00");
        assertLine(result.get(1), l1.brokerId(), 1, CommissionType.UPLINE_DIFFERENTIAL, "40000.00");
        assertLine(result.get(2), l2.brokerId(), 2, CommissionType.NETWORK_SAME_SLAB_BONUS, "15000.00");
        assertLine(result.get(3), l3.brokerId(), 3, CommissionType.UPLINE_DIFFERENTIAL, "15000.00");
        assertLine(result.get(4), l4.brokerId(), 4, CommissionType.NETWORK_SAME_SLAB_BONUS, "10000.00");
        assertThat(totalOf(result)).isEqualByComparingTo("240000.00");
    }

    /**
     * §17: four-level pure-differential chain, bottom-to-top 160/180/215/235
     * (spec's own top-to-bottom listing "235/215/180/160" reversed), 1000
     * sq.ft. No same-slab pair anywhere -- numerically unaffected by the
     * formula change. For a pure differential chain the telescoping sum
     * always equals area x top rate -- a useful independent identity check
     * alongside the explicit per-level values:
     * <pre>
     * B(160):               1000 x 160 = 160000        [SELLING_BROKER]
     * L1(180) vs B(160):    180>160 -> 1000x20 = 20000  [UPLINE_DIFFERENTIAL]
     * L2(215) vs L1(180):   215>180 -> 1000x35 = 35000  [UPLINE_DIFFERENTIAL]
     * L3(235) vs L2(215):   235>215 -> 1000x20 = 20000  [UPLINE_DIFFERENTIAL]
     * total = 235000 = 1000 x 235 (top rate) -- confirms the telescope.
     * </pre>
     * Same "compare-with-direct-downline-only" regression guard as §21:
     * comparing L2/L3 against the seller's 160 instead of their real
     * direct downlines would give 215-160=55000 and 235-160=75000 instead
     * of the correct 35000/20000 -- a completely different, wrong total
     * (310000 instead of 235000).
     */
    @Test
    void section17_fourLevelCompareWithDirectDownlineOnly() {
        ChainMember b = member(rate(160));
        ChainMember l1 = member(rate(180));
        ChainMember l2 = member(rate(215));
        ChainMember l3 = member(rate(235));
        List<CommissionLineItem> result = CommissionCalculationEngine.calculate(List.of(b, l1, l2, l3), BigDecimal.valueOf(1000));

        assertThat(result).hasSize(4);
        assertLine(result.get(0), b.brokerId(), 0, CommissionType.SELLING_BROKER, "160000.00");
        assertLine(result.get(1), l1.brokerId(), 1, CommissionType.UPLINE_DIFFERENTIAL, "20000.00");
        assertLine(result.get(2), l2.brokerId(), 2, CommissionType.UPLINE_DIFFERENTIAL, "35000.00");
        assertLine(result.get(3), l3.brokerId(), 3, CommissionType.UPLINE_DIFFERENTIAL, "20000.00");

        BigDecimal total = totalOf(result);
        assertThat(total).isEqualByComparingTo("235000.00");
        assertThat(total).isEqualByComparingTo(BigDecimal.valueOf(1000).multiply(rate(235))); // area x top rate, the telescope identity
        // The wrong ("compare with seller") total would be 310000 -- explicitly assert we do NOT get that.
        assertThat(total).isNotEqualByComparingTo("310000.00");
    }

    /**
     * §33: upline rate &lt; direct downline rate -&gt; upline receives
     * exactly ₹0, never a negative number. Constructed freely (the spec
     * only describes the rule, not a specific chain) -- a lower-rated
     * upline is a real, reachable state via manual demotion/override. Not
     * a same-slab pairing (rates differ), so unaffected by the formula
     * change.
     */
    @Test
    void section33_lowerRatedUplineReceivesZeroNeverNegative() {
        ChainMember b = member(rate(200));
        ChainMember a = member(rate(160)); // demoted/overridden below its own downline's rate
        List<CommissionLineItem> result = CommissionCalculationEngine.calculate(List.of(b, a), BigDecimal.valueOf(1000));

        assertThat(result).hasSize(2);
        assertLine(result.get(0), b.brokerId(), 0, CommissionType.SELLING_BROKER, "200000.00");
        CommissionLineItem uplineRow = result.get(1);
        assertThat(uplineRow.beneficiaryBrokerId()).isEqualTo(a.brokerId());
        assertThat(uplineRow.amount()).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(uplineRow.amount().signum()).isGreaterThanOrEqualTo(0); // explicitly never negative
        assertThat(totalOf(result)).isEqualByComparingTo("200000.00"); // A contributes nothing
    }

    /** A top-level broker selling directly, with no upline at all -- the simplest real case. */
    @Test
    void topLevelBrokerWithNoUplineProducesOnlyTheSellingBrokerRow() {
        ChainMember b = member(rate(160));
        List<CommissionLineItem> result = CommissionCalculationEngine.calculate(List.of(b), BigDecimal.valueOf(500));

        assertThat(result).hasSize(1);
        assertLine(result.get(0), b.brokerId(), 0, CommissionType.SELLING_BROKER, "80000.00");
    }

    /** §7: results are per-chain-position, independent of area precision -- a fractional plot area is handled the same way, rounded only at the final step (CLAUDE.md rule #2). */
    @Test
    void fractionalAreaRoundsOnlyAtTheFinalStep() {
        ChainMember b = member(rate(160));
        ChainMember a = member(rate(180));
        List<CommissionLineItem> result = CommissionCalculationEngine.calculate(List.of(b, a), new BigDecimal("1033.335"));

        // 1033.335 x 160 = 165333.6 -> rounds to 165333.60; 1033.335 x 20 = 20666.70
        assertLine(result.get(0), b.brokerId(), 0, CommissionType.SELLING_BROKER, "165333.60");
        assertLine(result.get(1), a.brokerId(), 1, CommissionType.UPLINE_DIFFERENTIAL, "20666.70");
    }

    @Test
    void rejectsAnEmptyChain() {
        assertThatThrownBy(() -> CommissionCalculationEngine.calculate(List.of(), BigDecimal.valueOf(1000)))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void rejectsNonPositiveArea() {
        ChainMember b = member(rate(160));
        assertThatThrownBy(() -> CommissionCalculationEngine.calculate(List.of(b), BigDecimal.ZERO))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> CommissionCalculationEngine.calculate(List.of(b), BigDecimal.valueOf(-100)))
                .isInstanceOf(IllegalArgumentException.class);
    }

    private static void assertLine(CommissionLineItem item, UUID expectedBeneficiary, int expectedLevel,
                                    CommissionType expectedType, String expectedAmount) {
        assertThat(item.beneficiaryBrokerId()).isEqualTo(expectedBeneficiary);
        assertThat(item.uplineLevel()).isEqualTo(expectedLevel);
        assertThat(item.type()).isEqualTo(expectedType);
        assertThat(item.amount()).isEqualByComparingTo(expectedAmount);
    }

    private static BigDecimal totalOf(List<CommissionLineItem> items) {
        return items.stream().map(CommissionLineItem::amount).reduce(BigDecimal.ZERO, BigDecimal::add);
    }
}
