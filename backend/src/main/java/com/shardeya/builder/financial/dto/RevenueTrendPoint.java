package com.shardeya.builder.financial.dto;

import java.math.BigDecimal;

/** One bar of B-08's 12-month RevenueTrendChart. month is "yyyy-MM" (IST). */
public record RevenueTrendPoint(String month, BigDecimal collected, long paymentCount) {
}
