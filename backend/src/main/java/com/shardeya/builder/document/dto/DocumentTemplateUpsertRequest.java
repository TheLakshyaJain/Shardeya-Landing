package com.shardeya.builder.document.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record DocumentTemplateUpsertRequest(
        @NotBlank @Size(min = 2, max = 120) String name,
        @NotBlank String bodyHtml,
        String headerHtml,
        String footerHtml
) {
}
