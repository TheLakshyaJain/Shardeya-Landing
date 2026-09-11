package com.shardeya.foundation.importexport.dto;

import java.util.List;
import java.util.Map;

public record ReportPreviewResponse(List<ReportColumn> columns, List<Map<String, Object>> rows, int totalCount, int page, int pageSize) {
}
