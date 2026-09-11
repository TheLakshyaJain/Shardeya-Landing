package com.shardeya.builder.financial.dto;

import java.math.BigDecimal;
import java.util.List;

/** B-08 §14.1's six summary cards, computed exactly as its own §7 specifies. */
public record FinancialSummaryResponse(
        BigDecimal totalRevenueAllTime,
        BigDecimal revenueThisMonth,
        BigDecimal revenueThisYear,
        BigDecimal pendingCollections,
        OverdueSummary overdueInstalments,
        BigDecimal totalBrokerCommissionPaid,
        // PaymentModeBreakdown (§6) needs an aggregate over ALL payments in
        // range, not just one cursor page -- computed here rather than
        // making the frontend page through /payments and tally client-side.
        List<ModeBreakdown> paymentModeBreakdown,
        boolean scoped,
        int scopedProjectCount,
        int totalProjectCount
) {
    public record OverdueSummary(BigDecimal amount, long count) {
    }

    public record ModeBreakdown(String mode, BigDecimal amount, long count) {
    }
}
