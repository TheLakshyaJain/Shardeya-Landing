package com.shardeya.builder.project.dto;

import java.util.List;

public record GridConfigResponse(Integer rows, Integer cols, List<GridCell> blockedCells) {
}
