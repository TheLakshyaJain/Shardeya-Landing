package com.shardeya.builder.stats.dto;

import java.math.BigDecimal;

public record BreakdownSlice(String label, long count, BigDecimal value) {
}
