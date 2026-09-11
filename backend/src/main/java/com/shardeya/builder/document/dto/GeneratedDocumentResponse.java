package com.shardeya.builder.document.dto;

import java.time.Instant;
import java.util.UUID;

public record GeneratedDocumentResponse(
        UUID id, String docType, String documentNumber, String entityType, UUID entityId,
        UUID mediaId, String language, UUID generatedBy, Instant generatedAt
) {
}
