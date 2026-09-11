package com.shardeya.builder.sale.dto;

import java.util.UUID;

public record PlotDocumentResponse(UUID id, String docType, String label, UUID mediaId, boolean sensitive) {
}
