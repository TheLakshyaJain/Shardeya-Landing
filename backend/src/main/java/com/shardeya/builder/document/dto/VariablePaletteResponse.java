package com.shardeya.builder.document.dto;

import java.util.List;
import java.util.Map;

public record VariablePaletteResponse(List<String> topLevel, Map<String, List<String>> collections) {
}
