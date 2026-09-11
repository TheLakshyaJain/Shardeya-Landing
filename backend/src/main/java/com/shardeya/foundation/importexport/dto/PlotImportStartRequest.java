package com.shardeya.foundation.importexport.dto;

import jakarta.validation.constraints.NotNull;

import java.util.UUID;

public record PlotImportStartRequest(
        @NotNull(message = "error.import.mediaRequired") UUID mediaId,
        String duplicateMode, // SKIP (default) | UPDATE_EXISTING | FAIL
        boolean autoPlace
) {
}
