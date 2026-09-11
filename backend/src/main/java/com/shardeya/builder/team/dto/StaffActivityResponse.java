package com.shardeya.builder.team.dto;

import java.time.Instant;

public record StaffActivityResponse(long leadsAssigned, long interactionsConducted, Instant lastLoginAt) {
}
