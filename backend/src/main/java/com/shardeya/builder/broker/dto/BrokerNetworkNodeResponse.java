package com.shardeya.builder.broker.dto;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * 06-BROKER-NETWORK-ENGINE.md §28 network tree view -- one row per
 * DESIGNATION broker in the org. Flat, with {@code uplineBrokerId}
 * pointers; the frontend assembles the tree client-side (org-wide broker
 * counts are small enough that this is simpler than a server-built
 * nested structure).
 */
public record BrokerNetworkNodeResponse(
        UUID id, String fullName, UUID uplineBrokerId, String designationName, String designationNameHi,
        BigDecimal currentCommissionRate, int personalSuccessfulBookings, int teamSuccessfulBookings, String status
) {
}
