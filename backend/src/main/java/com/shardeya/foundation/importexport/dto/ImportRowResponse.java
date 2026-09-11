package com.shardeya.foundation.importexport.dto;

import java.util.List;
import java.util.Map;
import java.util.UUID;

public record ImportRowResponse(
        UUID id, int rowNumber, Map<String, String> data, String status, List<RowError> errors
) {
    public record RowError(String column, String code, String messageKey, Map<String, Object> params) {
    }
}
