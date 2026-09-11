package com.shardeya.foundation.importexport.dto;

import java.util.UUID;

public record ImportJobResponse(
        UUID id, String status, int totalRows, int validRows, int invalidRows, int importedRows
) {
}
