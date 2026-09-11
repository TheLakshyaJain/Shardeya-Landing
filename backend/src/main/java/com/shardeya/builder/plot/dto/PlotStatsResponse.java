package com.shardeya.builder.plot.dto;

import java.math.BigDecimal;

public record PlotStatsResponse(
        long totalPlots, long available, long reserved, long sold,
        BigDecimal totalValue, BigDecimal availableValue, BigDecimal reservedValue, BigDecimal soldValue
) {
}
