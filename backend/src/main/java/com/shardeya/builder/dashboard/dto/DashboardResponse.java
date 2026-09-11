package com.shardeya.builder.dashboard.dto;

import java.time.Instant;
import java.util.List;
import java.util.Map;

public record DashboardResponse(
        Map<String, DashboardCard> cards,
        List<DashboardAlert> alerts,
        Instant generatedAt,
        boolean isOnboarding,
        boolean scoped,
        int scopedProjectCount,
        int totalProjectCount
) {
    public record DashboardAlert(String type, long count, java.math.BigDecimal amount) {
    }
}
