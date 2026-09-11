package com.shardeya.builder.dashboard.dto;

import java.math.BigDecimal;

/** One of B-01 §11.1's ten summary cards. amount is null for count-only cards. */
public record DashboardCard(Object value, BigDecimal amount, String link) {

    public static DashboardCard of(long value, String link) {
        return new DashboardCard(value, null, link);
    }

    public static DashboardCard ofAmount(BigDecimal value, String link) {
        return new DashboardCard(value, null, link);
    }

    public static DashboardCard ofCountAndAmount(long count, BigDecimal amount, String link) {
        return new DashboardCard(count, amount, link);
    }
}
