package com.shardeya.builder.plot.dto;

import com.shardeya.builder.plot.Plot;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

/** Written as each generated plot's individual starting values -- not a link back to a template (B-06 §7). */
public record QuickCreateSharedPropertiesRequest(
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
        String remarks
) {
}
