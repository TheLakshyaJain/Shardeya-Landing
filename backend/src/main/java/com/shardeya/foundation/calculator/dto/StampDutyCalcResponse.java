package com.shardeya.foundation.calculator.dto;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * M-08 §7/§10: rates are indicative -- effectiveFrom is always carried so a
 * stale client is visibly stale (rate changed mid-session -> server is
 * always authoritative, per the spec's own text), and the disclaimer is
 * always rendered client-side, never suppressed.
 */
public record StampDutyCalcResponse(
        BigDecimal stampDuty, BigDecimal registrationCharges, BigDecimal totalGovernmentCharges,
        String appliedGender, LocalDate effectiveFrom, String sourceNote
) {
}
