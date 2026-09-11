package com.shardeya.builder.broker;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * 06-BROKER-NETWORK-ENGINE.md §7 -- the frozen-at-BOOKED commission
 * calculation, as a PURE function with no DB/service dependencies of any
 * kind. Deliberately built and exhaustively unit-tested (see
 * {@code CommissionCalculationEngineTest}, covering every §16 fixture)
 * BEFORE any wiring into a sale/booking transaction (build-order step 5,
 * not this one) -- per the milestone's own explicit instruction, since
 * this is the single most money-critical piece of the whole feature and
 * must be provably correct in isolation first.
 *
 * <p>Callers are responsible for resolving each chain member's rate
 * BEFORE calling this method -- {@link #calculate} has no concept of
 * "current" vs "historical" rates, promotion timing, or which org/broker
 * anything belongs to. It only knows about the numbers it's handed.
 */
public final class CommissionCalculationEngine {

    public enum CommissionType { SELLING_BROKER, UPLINE_DIFFERENTIAL, NETWORK_SAME_SLAB_BONUS }

    /**
     * One link in the chain, bottom (selling broker) to top. The rate MUST
     * already be the frozen booking-time rate -- this class never looks
     * anything up.
     *
     * <p>{@code nextSlabRatePerSqft} is this member's OWN next-slab-up
     * rate (06-BROKER-NETWORK-ENGINE.md §7's revised same-slab formula),
     * resolved by the caller from the real {@code designation_slab}
     * config -- {@code null} means this member is already at the top slab
     * (no higher slab exists), which {@link #calculate} turns into an
     * explicit ₹0, never a fabricated rate or a fallback to the old flat
     * ₹10. Only ever read when this member turns out to be an UPLINE
     * sharing its direct downline's rate; irrelevant (and never read) for
     * the selling-broker row itself or for a differential row.
     */
    public record ChainMember(UUID brokerId, BigDecimal ratePerSqft, BigDecimal nextSlabRatePerSqft) {
        public ChainMember {
            Objects.requireNonNull(brokerId, "brokerId");
            Objects.requireNonNull(ratePerSqft, "ratePerSqft");
        }
    }

    /**
     * One row of the resulting commission tree. {@code directDownlineRatePerSqft}
     * is null only for the SELLING_BROKER row (level 0), which has no
     * downline to compare against -- every other row always has one, per
     * §7's own "each upline compares only with its direct downline" rule.
     */
    public record CommissionLineItem(
            UUID beneficiaryBrokerId, int uplineLevel, CommissionType type,
            BigDecimal beneficiaryRatePerSqft, BigDecimal directDownlineRatePerSqft, BigDecimal amount
    ) {
    }

    private static final int MONEY_SCALE = 2;

    private CommissionCalculationEngine() {
    }

    /**
     * §7's algorithm, verbatim (same-slab formula REVISED -- see below):
     * <pre>
     * S receives:  Q × rate(S)                                              [SELLING_BROKER]
     * walk up the upline chain: for each (upline U, its direct downline D on this chain):
     *     if   rate(U) &gt;  rate(D):  U receives Q × (rate(U) − rate(D))            [UPLINE_DIFFERENTIAL]
     *     elif rate(U) == rate(D):  U receives Q × (nextSlabRate(U) − rate(U))    [NETWORK_SAME_SLAB_BONUS]
     *                               (nextSlabRate(U) is the rate of the slab immediately
     *                                above U's current slab; ₹0 if U is already at the top slab)
     *     else (rate(U) &lt; rate(D)): U receives 0
     * </pre>
     *
     * <p><b>Same-slab formula history:</b> this branch originally paid a
     * flat ₹10/sq.ft. regardless of which slab the pair shared. Replaced
     * with the dynamic "next slab rate minus current shared rate" formula
     * above -- a same-slab pair now earns MORE the lower they are on the
     * ladder (₹20 at the bottom, shrinking toward ₹10 higher up, ₹0 at the
     * very top) rather than a fixed amount everywhere. See
     * {@code CommissionCalculationEngineTest}'s own class javadoc for the
     * full before/after and the lookup table. <b>Only this one number
     * changed</b> -- the trigger condition ({@code rate(U) == rate(D)}),
     * the {@code NETWORK_SAME_SLAB_BONUS} type, the "additional/uncapped,
     * never redistributed" rule, and independent-per-level evaluation are
     * all exactly as they were.
     *
     * <p><b>Non-negotiable rules, all enforced structurally by this
     * implementation (§7):</b>
     * <ul>
     *   <li>Each upline compares ONLY with its direct downline on the
     *       chain (the previous element in the list), never the original
     *       seller -- {@code chain.get(i - 1)}, never {@code chain.get(0)},
     *       for {@code i > 1}.</li>
     *   <li>Never negative: the {@code rate(U) < rate(D)} branch produces
     *       an explicit zero-amount UPLINE_DIFFERENTIAL row (not a
     *       negative one, and not simply omitted -- the row exists for
     *       audit completeness: §9's cancellation logic needs "a
     *       reversal record for every beneficiary in the tree... even
     *       uplines who did nothing wrong", which presumes every level
     *       IS represented). The same-slab branch is equally defensive:
     *       a next-slab rate below the current rate (should never happen
     *       given the real, ascending slab config) still clamps to ₹0
     *       rather than going negative, and a top-slab member (no next
     *       slab at all, {@code nextSlabRatePerSqft == null}) is an
     *       explicit ₹0, never a fabricated higher rate.</li>
     *   <li>The same-slab bonus is evaluated independently at every
     *       level and is never capped -- there is no running total or
     *       ceiling anywhere in this method; each level's amount depends
     *       only on that level's own rates.</li>
     * </ul>
     *
     * @param chain bottom (index 0, the selling broker) to top (last
     *              element, the highest upline with no further upline).
     *              Must contain at least one element.
     * @param areaSqft the booking's plot area in sq.ft. Must be positive.
     * @return one line item per chain element, in the same bottom-to-top order.
     */
    public static List<CommissionLineItem> calculate(List<ChainMember> chain, BigDecimal areaSqft) {
        if (chain == null || chain.isEmpty()) {
            throw new IllegalArgumentException("chain must contain at least the selling broker");
        }
        if (areaSqft == null || areaSqft.signum() <= 0) {
            throw new IllegalArgumentException("areaSqft must be positive");
        }

        List<CommissionLineItem> result = new ArrayList<>(chain.size());

        ChainMember seller = chain.get(0);
        result.add(new CommissionLineItem(seller.brokerId(), 0, CommissionType.SELLING_BROKER,
                seller.ratePerSqft(), null, money(areaSqft.multiply(seller.ratePerSqft()))));

        for (int level = 1; level < chain.size(); level++) {
            ChainMember upline = chain.get(level);
            ChainMember directDownline = chain.get(level - 1); // NEVER chain.get(0) -- §7's core rule.
            int cmp = upline.ratePerSqft().compareTo(directDownline.ratePerSqft());

            CommissionType type;
            BigDecimal amount;
            if (cmp > 0) {
                type = CommissionType.UPLINE_DIFFERENTIAL;
                amount = money(areaSqft.multiply(upline.ratePerSqft().subtract(directDownline.ratePerSqft())));
            } else if (cmp == 0) {
                type = CommissionType.NETWORK_SAME_SLAB_BONUS;
                BigDecimal nextRate = upline.nextSlabRatePerSqft();
                // null (top slab, no higher slab exists) -> explicit ₹0,
                // never a fabricated rate or a fallback to the old flat
                // ₹10 (§7's mandatory top-slab edge case).
                BigDecimal perSqft = nextRate == null ? BigDecimal.ZERO : nextRate.subtract(upline.ratePerSqft());
                if (perSqft.signum() < 0) {
                    // Defensive only -- the real slab config is always
                    // ascending, so nextRate > upline.ratePerSqft() should
                    // always hold whenever nextRate is non-null. Never
                    // surface a negative same-slab bonus regardless.
                    perSqft = BigDecimal.ZERO;
                }
                amount = money(areaSqft.multiply(perSqft));
            } else {
                // rate(U) < rate(D): never negative -- an explicit zero, not omitted (see javadoc above).
                type = CommissionType.UPLINE_DIFFERENTIAL;
                amount = money(BigDecimal.ZERO);
            }
            result.add(new CommissionLineItem(upline.brokerId(), level, type,
                    upline.ratePerSqft(), directDownline.ratePerSqft(), amount));
        }

        return result;
    }

    /** CLAUDE.md rule #2: BigDecimal, round HALF_UP only at this final step. */
    private static BigDecimal money(BigDecimal raw) {
        return raw.setScale(MONEY_SCALE, RoundingMode.HALF_UP);
    }
}
