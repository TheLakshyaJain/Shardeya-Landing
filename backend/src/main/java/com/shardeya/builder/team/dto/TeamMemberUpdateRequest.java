package com.shardeya.builder.team.dto;

import java.util.List;
import java.util.UUID;

public record TeamMemberUpdateRequest(
        String fullName,
        String email,
        String roleCode,
        List<UUID> projectAccess,
        Boolean allProjects
) {
}
