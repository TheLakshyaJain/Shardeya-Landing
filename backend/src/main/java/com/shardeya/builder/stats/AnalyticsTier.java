package com.shardeya.builder.stats;

import java.util.List;
import java.util.Set;

/**
 * B-15 §7 entitlement table: Basic (Free) = plot status pie + monthly sales
 * only; Advanced (Pro) = all ten charts; Full (Premium) = all charts +
 * custom date ranges + chart export + saved views. Only the chart-count
 * gate is enforced this round -- the Full-only extras (custom ranges,
 * export, saved views) are a deliberate scope trim (see CLAUDE.md).
 */
final class AnalyticsTier {

    static final String KEY = "ANALYTICS";
    static final List<String> ORDERED = List.of("BASIC", "ADVANCED", "FULL");

    private static final Set<String> BASIC_CHARTS = Set.of("plot-status-breakdown", "monthly-sales");

    private AnalyticsTier() {
    }

    static String minimumFor(String chartKey) {
        return BASIC_CHARTS.contains(chartKey) ? "BASIC" : "ADVANCED";
    }
}
