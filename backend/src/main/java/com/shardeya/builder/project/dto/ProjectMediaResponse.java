package com.shardeya.builder.project.dto;

import java.util.UUID;

public record ProjectMediaResponse(UUID mediaId, String role, int sortOrder) {
}
