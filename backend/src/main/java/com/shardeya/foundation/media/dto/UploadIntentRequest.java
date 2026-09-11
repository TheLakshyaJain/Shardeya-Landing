package com.shardeya.foundation.media.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;

public record UploadIntentRequest(
        @NotBlank(message = "error.media.filenameRequired") String filename,
        @NotBlank(message = "error.media.mimeTypeRequired") String mimeType,
        @Positive(message = "error.media.sizeInvalid") long sizeBytes,
        @NotBlank(message = "error.media.purposeRequired") String purpose,
        String entityType,
        String entityId,
        // M3: government ID scans (and any future sensitive document) route
        // to the sensitive bucket instead of standard -- CLAUDE.md rule #6.
        // Missing/omitted deserializes to false (Jackson's record support
        // uses the primitive default for an absent property), so every
        // existing caller (project cover, plot import, etc.) is unaffected.
        boolean sensitive
) {
}
