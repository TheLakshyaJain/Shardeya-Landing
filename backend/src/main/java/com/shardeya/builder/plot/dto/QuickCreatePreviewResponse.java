package com.shardeya.builder.plot.dto;

import java.util.List;

public record QuickCreatePreviewResponse(
        List<PlotNumberPreview> plotNumbers,
        int totalCount,
        int quotaUsed,
        int quotaLimit,
        boolean withinQuota,
        int currentPlotCount,
        int declaredPlotCount,
        boolean withinDeclaredCount
) {
    public record PlotNumberPreview(String plotNumber, boolean collidesWithExisting, boolean collidesWithinRequest) {
    }
}
