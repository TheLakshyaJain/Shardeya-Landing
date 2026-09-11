package com.shardeya.builder.broker.dto;

import java.math.BigDecimal;

/**
 * 06-BROKER-NETWORK-ENGINE.md §29, build-order step 10 -- the org-wide
 * equivalent of BrokerCommissionSummaryResponse, for the builder/admin
 * network overview. Same CANCELLED-exclusion rule.
 */
public record NetworkCommissionSummaryResponse(
        long totalDesignationBrokers,
        BigDecimal totalCommissionEarned,
        BigDecimal sellingBrokerEarned, BigDecimal uplineDifferentialEarned, BigDecimal sameSlabBonusEarned,
        BigDecimal totalCommissionReleased, BigDecimal totalCommissionPending
) {
}
