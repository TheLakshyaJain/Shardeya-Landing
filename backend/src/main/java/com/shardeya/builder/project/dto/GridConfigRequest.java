package com.shardeya.builder.project.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

import java.util.List;

public record GridConfigRequest(
        @NotNull(message = "error.grid.dimensionsRequired") @Min(value = 1, message = "error.grid.dimensionsInvalid") @Max(value = 500, message = "error.grid.dimensionsInvalid") Integer rows,
        @NotNull(message = "error.grid.dimensionsRequired") @Min(value = 1, message = "error.grid.dimensionsInvalid") @Max(value = 500, message = "error.grid.dimensionsInvalid") Integer cols,
        List<GridCell> blockedCells
) {
}
