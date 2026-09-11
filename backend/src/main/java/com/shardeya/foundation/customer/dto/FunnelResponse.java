package com.shardeya.foundation.customer.dto;

public record FunnelResponse(long interested, long siteVisitScheduled, long siteVisitDone, long followingUp,
                              long dealClosed, long lost) {
}
