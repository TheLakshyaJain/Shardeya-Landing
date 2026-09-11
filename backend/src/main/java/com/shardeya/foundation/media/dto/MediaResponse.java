package com.shardeya.foundation.media.dto;

import java.util.Map;
import java.util.UUID;

public record MediaResponse(
        UUID id, String status, String url, Map<String, String> derivatives,
        Integer width, Integer height, String mimeType, long sizeBytes
) {
}
