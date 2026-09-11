package com.shardeya.builder.plot.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

public record PlotResponse(
        UUID id, UUID projectId, String plotNumber, String status,
        String reservedFor, LocalDate reservedUntil,
        BigDecimal sizeValue, String sizeUnit, BigDecimal sizeSqft,
        String facing, BigDecimal price, BigDecimal pricePerUnit,
        boolean isGarden, boolean isCorner, boolean isHot, String remarks,
        Integer gridRow, Integer gridCol, UUID currentSaleId
) {
}
