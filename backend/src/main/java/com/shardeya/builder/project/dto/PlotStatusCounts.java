package com.shardeya.builder.project.dto;

public record PlotStatusCounts(long total, long available, long reserved, long sold) {

    public static final PlotStatusCounts EMPTY = new PlotStatusCounts(0, 0, 0, 0);
}
