package com.shardeya.builder.project.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.util.UUID;

public record ProjectMediaAttachRequest(
        @NotNull(message = "error.media.idRequired") UUID mediaId,
        @NotBlank(message = "error.project.mediaRoleRequired") String role,
        int sortOrder
) {
}
