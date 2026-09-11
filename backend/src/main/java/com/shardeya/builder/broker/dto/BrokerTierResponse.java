package com.shardeya.builder.broker.dto;

import java.math.BigDecimal;
import java.util.UUID;

public record BrokerTierResponse(
        UUID id, String name, String nameHi, int minDeals, Integer maxDeals,
        String bonusType, BigDecimal bonusValue, String perksDescription, int sortOrder, boolean active
) {
}
