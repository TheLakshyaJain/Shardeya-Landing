package com.shardeya.builder.plot.dto;

import java.util.List;
import java.util.UUID;

public record BulkPositionRequest(List<Placement> placements) {

    public record Placement(UUID plotId, int gridRow, int gridCol) {
    }
}
