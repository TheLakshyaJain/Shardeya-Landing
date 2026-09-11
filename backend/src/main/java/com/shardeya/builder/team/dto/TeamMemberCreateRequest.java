package com.shardeya.builder.team.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.util.List;
import java.util.UUID;

/** Field validations mirror 03-BUILDER-MODULES.md B-12 §11. */
public record TeamMemberCreateRequest(
        @NotBlank(message = "error.team.fullNameRequired")
        @Size(min = 2, max = 100, message = "error.team.fullNameInvalid")
        String fullName,

        @NotBlank(message = "error.team.mobileRequired")
        @Pattern(regexp = "^[6-9]\\d{9}$", message = "error.team.mobileInvalid")
        String mobile,

        String email,

        @NotNull(message = "error.team.roleRequired")
        String roleCode,

        /** null/empty = ALL projects (default per §18.2); non-empty = SCOPED to exactly these. */
        List<UUID> projectAccess,

        boolean sendInvite
) {
}
