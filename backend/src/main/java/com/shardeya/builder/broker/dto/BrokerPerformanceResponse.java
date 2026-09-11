package com.shardeya.builder.broker.dto;

import java.math.BigDecimal;

/**
 * B-14 §20.6 BrokerPerformancePanel: deals, revenue generated, commission
 * earned/pending, conversion rate.
 *
 * <p>totalCommissionReleased is null for PERCENTAGE/FIXED brokers (no
 * release concept applies -- their whole commission is payable
 * immediately at sale time) and a real figure for DESIGNATION brokers
 * (06-BROKER-NETWORK-ENGINE.md §8a): kept visible alongside
 * commissionDue per §8a's own "keep Released visible too if useful, but
 * Due is the actionable one."
 */
public record BrokerPerformanceResponse(
        int totalDealsAttributed, int dealsCompleted, int dealsActive, int dealsCancelled,
        BigDecimal totalRevenueGenerated, BigDecimal totalCommissionEarned, BigDecimal totalCommissionPaid,
        BigDecimal totalCommissionReleased, BigDecimal commissionDue, BigDecimal conversionRatePct
) {
}
