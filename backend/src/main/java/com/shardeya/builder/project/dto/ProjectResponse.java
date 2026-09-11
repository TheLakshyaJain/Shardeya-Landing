package com.shardeya.builder.project.dto;

import java.util.UUID;

public record ProjectResponse(
        UUID id, String name, String projectType, String status, String city, String locality,
        UUID coverMediaId, PlotStatusCounts plotCounts
) {
}
