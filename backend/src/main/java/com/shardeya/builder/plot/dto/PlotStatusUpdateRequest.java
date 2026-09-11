package com.shardeya.builder.plot.dto;

import com.shardeya.builder.plot.Plot;
import jakarta.validation.constraints.NotNull;

import java.time.LocalDate;

public record PlotStatusUpdateRequest(
        @NotNull(message = "error.plot.statusRequired") Plot.Status status,
        String reservedFor,
        LocalDate reservedUntil
) {
}
