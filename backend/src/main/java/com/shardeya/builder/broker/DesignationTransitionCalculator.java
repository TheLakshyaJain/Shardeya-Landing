package com.shardeya.builder.broker;

import java.math.BigDecimal;

/**
 * 06-BROKER-NETWORK-ENGINE.md §6/§9, build-order steps 7+9 -- a pure,
 * dependency-free comparison between a broker's current rate and the rate
 * their now-current team-sales count resolves to. Step 7's promotion path
 * only ever accepts {@link Direction#PROMOTION}; step 9's
 * cancellation-driven re-evaluation is the first caller that also acts on
 * {@link Direction#DEMOTION} -- both share this exact same comparison so
 * "what counts as higher/lower" can never drift between the two paths.
 */
public final class DesignationTransitionCalculator {

    public enum Direction { PROMOTION, DEMOTION, NO_CHANGE }

    private DesignationTransitionCalculator() {
    }

    public static Direction compare(BigDecimal currentRate, BigDecimal candidateRate) {
        BigDecimal current = currentRate == null ? BigDecimal.ZERO : currentRate;
        int cmp = candidateRate.compareTo(current);
        if (cmp > 0) return Direction.PROMOTION;
        if (cmp < 0) return Direction.DEMOTION;
        return Direction.NO_CHANGE;
    }
}
