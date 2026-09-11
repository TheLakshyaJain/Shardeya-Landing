package com.shardeya.builder.document.dto;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record DocumentTemplateResponse(
        UUID id, UUID orgId, boolean systemDefault, String docType, String name, String language,
        String bodyHtml, String headerHtml, String footerHtml, List<String> variables,
        long version, boolean active, Instant updatedAt
) {
}
