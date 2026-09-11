package com.shardeya.builder.broker.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * 06-BROKER-NETWORK-ENGINE.md §26/§29, build-order step 10 -- one
 * designation_history row (AUTOMATIC promotion/demotion, MANUAL override,
 * or CANCELLATION_REVERSAL), denormalised for display the same way
 * BookingCommissionResponse already denormalises project/plot/buyer names
 * rather than making the frontend do its own lookups. previousDesignation*
 * is null only for a broker's very first designation (assigned at creation,
 * never itself written as a history row).
 */
public record DesignationHistoryResponse(
        UUID id, UUID brokerId, String brokerName,
        String previousDesignationName, String previousDesignationNameHi, BigDecimal previousRate,
        String newDesignationName, String newDesignationNameHi, BigDecimal newRate,
        String changeType, String reason, Instant effectiveAt, String changedByName
) {
}
