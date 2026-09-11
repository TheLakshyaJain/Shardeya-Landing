package com.shardeya.builder.plot.dto;

import com.shardeya.builder.plot.Plot;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

/**
 * B-03 §7 "Bulk operations": pricePerSqft is applied per-plot against each
 * plot's OWN size_sqft (not a flat price for every selected plot) — "select
 * 40 plots → set price ₹1,800/sqft → each plot's price computed from its own
 * size" is the actual real-world workflow (repricing a block).
 */
public record BulkUpdateRequest(List<UUID> plotIds, BigDecimal pricePerSqft, Plot.Status status) {
}
