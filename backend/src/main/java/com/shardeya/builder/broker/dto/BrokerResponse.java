package com.shardeya.builder.broker.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * B-14 §20.1 BrokerTable row -- list view, no bank details (SENSITIVE_VIEW,
 * separate endpoint). The designation/network fields (from
 * uplineBrokerId onward) are null/zero for PERCENTAGE and FIXED brokers,
 * which don't participate in the hierarchy (06-BROKER-NETWORK-ENGINE.md §0).
 */
public record BrokerResponse(
        UUID id, String fullName, String mobile, String email, String cityArea, String firmName,
        String commissionType, BigDecimal commissionPct, BigDecimal commissionFixed, boolean perProjectRatesEnabled,
        UUID tierId, String tierName, int dealsClosedCount,
        BigDecimal totalCommissionEarned, BigDecimal totalCommissionPaid, BigDecimal commissionDue,
        Instant lastActiveAt, String status,
        UUID uplineBrokerId, UUID currentDesignationId, String currentDesignationName, String currentDesignationNameHi,
        BigDecimal currentCommissionRate, int personalSuccessfulBookings, int teamSuccessfulBookings, boolean designationManuallyOverridden
) {
}
