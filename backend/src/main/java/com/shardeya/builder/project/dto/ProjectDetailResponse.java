package com.shardeya.builder.project.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public record ProjectDetailResponse(
        UUID id, String name, String projectType, String status,
        String address, String locality, String city, String stateCode, String pincode, String googleMapsUrl,
        BigDecimal totalAreaValue, String totalAreaUnit, BigDecimal totalAreaSqft,
        int declaredPlotCount, LocalDate launchDate, LocalDate expectedCompletionDate,
        String description, List<ApprovalTag> approvals, String reraNumber,
        UUID coverMediaId, UUID layoutMediaId, UUID brochureMediaId,
        Integer gridRows, Integer gridCols,
        PlotStatusCounts plotCounts, long declaredVsActualDelta
) {
}
