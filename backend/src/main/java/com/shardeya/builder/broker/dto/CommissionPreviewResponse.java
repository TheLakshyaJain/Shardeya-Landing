package com.shardeya.builder.broker.dto;

import java.math.BigDecimal;

/** B-14 §6 CommissionPreviewCard: "enter a deal value -> see the resolved commission and which rule applied -- removes all ambiguity." */
public record CommissionPreviewResponse(
        BigDecimal baseCommission, BigDecimal tierBonus, BigDecimal totalCommission,
        String appliedScope, String appliedCommissionType, BigDecimal appliedRateValue,
        String tierName, boolean usedBrokerDefault
) {
}
