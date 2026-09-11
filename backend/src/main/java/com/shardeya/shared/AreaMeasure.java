package com.shardeya.shared;

import java.math.BigDecimal;

/**
 * Area is always dual-stored (CLAUDE.md rule #3): {@code value} + {@code unit}
 * exactly as entered, plus {@code sqft} — the derived, canonical figure every
 * filter/sort/comparison uses. Never compare or sort on {@code value} directly;
 * two plots in different units are only comparable via {@code sqft}.
 */
public record AreaMeasure(BigDecimal value, String unit, BigDecimal sqft) {
}
