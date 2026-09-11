package com.shardeya.builder.document.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;

import java.util.List;
import java.util.UUID;

public record BulkGenerateRequest(
        @NotBlank String docType,
        @NotEmpty @Size(max = 500) List<UUID> entityIds,
        @NotBlank String language
) {
}
