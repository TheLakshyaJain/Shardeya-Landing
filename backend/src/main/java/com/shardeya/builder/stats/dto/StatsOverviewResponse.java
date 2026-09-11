package com.shardeya.builder.stats.dto;

import java.math.BigDecimal;
import java.time.Instant;

/** B-15 §7 KpiStrip. Ratios are {@code null} (never NaN/0%) when the denominator is zero -- B-15 §10. */
public record StatsOverviewResponse(
        BigDecimal conversionRate, BigDecimal avgDealValue, Double avgDaysToClose,
        BigDecimal collectionEfficiency, Instant dataAsOf
) {
}
