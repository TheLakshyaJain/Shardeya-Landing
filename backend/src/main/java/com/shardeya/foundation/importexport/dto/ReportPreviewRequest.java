package com.shardeya.foundation.importexport.dto;

import java.util.Map;

public record ReportPreviewRequest(Map<String, String> filters, Integer page) {

    public Map<String, String> filtersOrEmpty() {
        return filters == null ? Map.of() : filters;
    }

    public int pageOrDefault() {
        return page == null ? 0 : Math.max(0, page);
    }
}
