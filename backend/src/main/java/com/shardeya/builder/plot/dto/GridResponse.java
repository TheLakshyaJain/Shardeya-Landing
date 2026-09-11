package com.shardeya.builder.plot.dto;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Deliberately compact (B-03 §4): tuple order per plot is
 * [row, col, plotNumber, statusCode, sizeSqft, isHot] — no repeated field
 * names, ~45 bytes/plot so 5,000 plots stays around 225KB (gzips to ~40KB).
 */
public record GridResponse(
        int rows, int cols,
        List<int[]> blocked,
        List<Object[]> plots,
        Legend legend,
        List<UUID> unplaced
) {
    public record Legend(Map<String, String> statusCodes) {
    }
}
