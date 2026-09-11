package com.shardeya.builder.stats.dto;

import java.math.BigDecimal;

public record MonthPoint(String month, BigDecimal value, long count) {
}
