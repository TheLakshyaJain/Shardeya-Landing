package com.shardeya.builder.plot.dto;

import com.shardeya.builder.plot.Plot;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.LocalDate;

/** Field validations mirror 03-BUILDER-MODULES.md B-03 §11. status is deliberately NOT accepted here beyond AVAILABLE/RESERVED — see PlotService. */
public record PlotCreateRequest(
        @NotBlank(message = "error.plot.numberRequired")
        @Size(min = 1, max = 30, message = "error.plot.numberInvalid")
        String plotNumber,

        Plot.Status status,
        String reservedFor,
        LocalDate reservedUntil,

        @NotNull(message = "error.plot.sizeRequired")
        @DecimalMin(value = "0.0001", message = "error.plot.sizeInvalid")
        @Max(value = 10_000_000, message = "error.plot.sizeInvalid")
        BigDecimal sizeValue,

        @NotBlank(message = "error.plot.sizeUnitRequired")
        String sizeUnit,

        Plot.Facing facing,

        @NotNull(message = "error.plot.priceRequired")
        @PositiveOrZero(message = "error.plot.priceInvalid")
        BigDecimal price,

        boolean isGarden,
        boolean isCorner,
        boolean isHot,

        @Size(max = 2000, message = "error.plot.remarksInvalid")
        String remarks,

        @Min(value = 0, message = "error.plot.positionInvalid") Integer gridRow,
        @Min(value = 0, message = "error.plot.positionInvalid") Integer gridCol
) {
}
