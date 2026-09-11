package com.shardeya.builder.broker.dto;

import java.math.BigDecimal;

/**
 * 06-BROKER-NETWORK-ENGINE.md §26/§27, build-order step 10 -- the money
 * aggregates a DESIGNATION broker's own commission tree implies, that
 * nothing else in the API surface already exposes in summed form
 * (BookingCommissionResponse is per-row, this is the roll-up). Everything
 * else §26/§27 names (upline, direct/total downline, next designation) is
 * deliberately NOT duplicated here -- it's derivable client-side from data
 * the frontend already fetches (GET /brokers/network for the tree/downline
 * counts, GET /designation-slabs for the next-slab lookup), matching
 * NetworkTreePage's own established "small org-wide counts, cheaper to
 * assemble client-side than invent a second nested response shape" call.
 * CANCELLED booking_commission rows are excluded from every figure here,
 * mirroring fn_broker_partner_commission_earned_trigger's (V6_015) own
 * identical exclusion for PERCENTAGE brokers.
 */
public record BrokerCommissionSummaryResponse(
        BigDecimal personalCommissionEarned, BigDecimal teamCommissionEarned,
        BigDecimal sellingBrokerEarned, BigDecimal uplineDifferentialEarned, BigDecimal sameSlabBonusEarned,
        BigDecimal commissionReleased, BigDecimal commissionPending
) {
}
