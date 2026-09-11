package com.shardeya.foundation.importexport.dto;

import jakarta.validation.constraints.NotBlank;

import java.util.Map;

public record ReportExportRequest(Map<String, String> filters, @NotBlank String format) {

    public Map<String, String> filtersOrEmpty() {
        return filters == null ? Map.of() : filters;
    }
}
