package com.shardeya.builder.broker.dto;

import java.math.BigDecimal;
import java.util.UUID;

public record DesignationSlabResponse(
        UUID id, String name, String nameHi, int minTeamSales, Integer maxTeamSales,
        BigDecimal ratePerSqft, short sortOrder
) {
}
