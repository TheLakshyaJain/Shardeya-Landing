package com.shardeya.builder.stats.dto;

public record FunnelStage(String stage, long count, Double conversionFromFirst) {
}
