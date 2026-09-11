package com.shardeya.foundation.importexport.dto;

import java.util.List;

public record ReportDefinitionSummary(
        String code, String profile, String nameEn, String nameHi, String descriptionKey,
        List<Object> supportedFilters, List<String> supportedFormats, boolean canExport
) {
}
