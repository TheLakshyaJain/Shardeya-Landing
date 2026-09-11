package com.shardeya.builder.document.dto;

import jakarta.validation.constraints.NotNull;

import java.util.UUID;

public record TemplatePreviewRequest(@NotNull UUID sampleEntityId) {
}
