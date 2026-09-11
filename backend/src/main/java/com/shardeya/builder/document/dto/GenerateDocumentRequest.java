package com.shardeya.builder.document.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.util.UUID;

public record GenerateDocumentRequest(
        @NotBlank String docType,
        @NotNull UUID entityId,
        UUID templateId,
        @NotBlank String language
) {
}
