package com.shardeya.foundation.calculator.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;

public record PlotSizeRequest(
        @NotNull(message = "error.calc.dimensionRequired") @DecimalMin(value = "0.01", message = "error.calc.dimensionInvalid") @Max(value = 100_000, message = "error.calc.dimensionInvalid") BigDecimal length,
        @NotNull(message = "error.calc.dimensionRequired") @DecimalMin(value = "0.01", message = "error.calc.dimensionInvalid") @Max(value = 100_000, message = "error.calc.dimensionInvalid") BigDecimal width,
        @NotBlank(message = "error.calc.unitRequired") String unit,
        // Not in 02-FOUNDATION-MODULES.md M-08's literal request shape, but
        // Bigha's conversion factor is state-dependent (§3.4.1) and the
        // calculator has no other way to know which state applies — added
        // as optional so a caller with no org/project state context can
        // still get the universal (non-Bigha-specific) conversions.
        String stateCode
) {
}
