package com.shardeya.builder.stats.dto;

import java.math.BigDecimal;

public record CollectionVsTargetPoint(String month, BigDecimal actual, BigDecimal target) {
}
