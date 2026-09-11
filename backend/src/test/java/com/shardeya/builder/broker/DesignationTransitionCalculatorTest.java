package com.shardeya.builder.broker;

import com.shardeya.builder.broker.DesignationTransitionCalculator.Direction;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 06-BROKER-NETWORK-ENGINE.md §6/§9 -- pure comparison, no DB, no Spring.
 * Step 7's promotion path only ever acts on PROMOTION; step 9's
 * cancellation-driven re-evaluation is the first caller that also acts on
 * DEMOTION -- this is the shared "which direction is this" decision both
 * paths funnel through, so it's fixture-tested once, thoroughly, here.
 */
class DesignationTransitionCalculatorTest {

    @Test
    void aHigherCandidateRateIsAPromotion() {
        // e.g. Business Executive(160) -> Senior Business Executive(180).
        assertThat(DesignationTransitionCalculator.compare(BigDecimal.valueOf(160), BigDecimal.valueOf(180)))
                .isEqualTo(Direction.PROMOTION);
    }

    @Test
    void aLowerCandidateRateIsADemotion() {
        // e.g. team sales corrected downward by a cancellation: Senior Business Executive(180) -> Business Executive(160).
        assertThat(DesignationTransitionCalculator.compare(BigDecimal.valueOf(180), BigDecimal.valueOf(160)))
                .isEqualTo(Direction.DEMOTION);
    }

    @Test
    void anIdenticalCandidateRateIsNoChange() {
        assertThat(DesignationTransitionCalculator.compare(BigDecimal.valueOf(200), BigDecimal.valueOf(200)))
                .isEqualTo(Direction.NO_CHANGE);
    }

    // A brand-new DESIGNATION broker (currentCommissionRate not yet set --
    // shouldn't happen in practice, BrokerPartnerService.create() always
    // sets it, but the pure function must still behave sensibly) treats a
    // null current rate as zero, so any real slab rate is a promotion.
    @Test
    void aNullCurrentRateIsTreatedAsZeroSoAnyRealRateIsAPromotion() {
        assertThat(DesignationTransitionCalculator.compare(null, BigDecimal.valueOf(160)))
                .isEqualTo(Direction.PROMOTION);
    }

    // §5's full slab ladder, both directions -- every adjacent pair.
    @Test
    void everyAdjacentSlabPairInBothDirections() {
        int[] rates = {160, 180, 200, 215, 225, 235, 245, 255};
        for (int i = 0; i < rates.length - 1; i++) {
            assertThat(DesignationTransitionCalculator.compare(BigDecimal.valueOf(rates[i]), BigDecimal.valueOf(rates[i + 1])))
                    .as("rate %d -> %d should be a promotion", rates[i], rates[i + 1])
                    .isEqualTo(Direction.PROMOTION);
            assertThat(DesignationTransitionCalculator.compare(BigDecimal.valueOf(rates[i + 1]), BigDecimal.valueOf(rates[i])))
                    .as("rate %d -> %d should be a demotion", rates[i + 1], rates[i])
                    .isEqualTo(Direction.DEMOTION);
        }
    }
}
