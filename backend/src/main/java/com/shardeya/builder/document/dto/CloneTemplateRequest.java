package com.shardeya.builder.document.dto;

import jakarta.validation.constraints.NotBlank;

public record CloneTemplateRequest(
        @NotBlank String docType,
        @NotBlank String language
) {
}
