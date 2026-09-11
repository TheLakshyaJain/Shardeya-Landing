package com.shardeya.foundation.importexport.dto;

/** {@code type} in {"TEXT","NUMBER","MONEY","DATE"} -- drives Excel number format + frontend column alignment. */
public record ReportColumn(String key, String labelKey, String type) {
}
