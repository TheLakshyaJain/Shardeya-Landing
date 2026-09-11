package com.shardeya.foundation.auth.dto;

import java.util.UUID;

public record UserSummary(
        UUID id, String fullName, String mobile, String email, String role, boolean isOwner, String language) {
}
