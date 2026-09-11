package com.shardeya.builder.team.dto;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record TeamMemberResponse(
        UUID id,
        String fullName,
        String mobile,
        String email,
        String roleCode,
        String status,
        boolean owner,
        boolean allProjects,
        List<UUID> projectAccess,
        Instant lastLoginAt,
        Instant createdAt,
        String inviteStatus,
        Instant inviteSentAt
) {
}
