package com.shardeya.foundation.auth.dto;

/** M1 scope: just the plan code — the M-04 slice this milestone delivers only needs a static "Free" badge. Full entitlement/limit enforcement is M-09. */
public record EntitlementsSummary(String plan) {
}
