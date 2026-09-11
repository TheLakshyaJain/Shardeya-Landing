package com.shardeya.builder.plot.dto;

import com.shardeya.builder.plot.Plot;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

public record PlotUpdateRequest(
        @Size(min = 1, max = 30, message = "error.plot.numberInvalid") String plotNumber,
        @Max(value = 10_000_000, message = "error.plot.sizeInvalid") BigDecimal sizeValue,
        String sizeUnit,
        Plot.Facing facing,
        @PositiveOrZero(message = "error.plot.priceInvalid") BigDecimal price,
        Boolean isGarden,
        Boolean isCorner,
        Boolean isHot,
        @Size(max = 2000, message = "error.plot.remarksInvalid") String remarks
) {
}
