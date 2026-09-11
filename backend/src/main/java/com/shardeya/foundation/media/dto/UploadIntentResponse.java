package com.shardeya.foundation.media.dto;

import java.util.UUID;

public record UploadIntentResponse(UUID mediaId, String uploadUrl, long expiresIn) {
}
