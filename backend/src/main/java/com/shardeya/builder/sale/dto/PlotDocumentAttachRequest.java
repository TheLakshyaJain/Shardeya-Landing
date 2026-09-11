package com.shardeya.builder.sale.dto;

import com.shardeya.builder.sale.PlotDocument;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record PlotDocumentAttachRequest(
        @NotNull(message = "error.document.typeRequired") PlotDocument.DocType docType,
        @Size(max = 120, message = "error.document.labelInvalid") String label,
        @NotNull(message = "error.document.mediaRequired") java.util.UUID mediaId
) {
}
