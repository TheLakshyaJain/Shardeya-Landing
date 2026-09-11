package com.shardeya.builder.plot.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

public record PlotPositionRequest(
        @NotNull(message = "error.plot.positionRequired") @Min(value = 0, message = "error.plot.positionInvalid") Integer gridRow,
        @NotNull(message = "error.plot.positionRequired") @Min(value = 0, message = "error.plot.positionInvalid") Integer gridCol
) {
}
