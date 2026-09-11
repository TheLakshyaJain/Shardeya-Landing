package com.shardeya.foundation.auth.dto;

import jakarta.validation.constraints.NotBlank;

public record LoginRequest(
        @NotBlank(message = "error.auth.identifierRequired") String identifier,
        @NotBlank(message = "error.auth.passwordRequired") String password,
        boolean rememberMe
) {
}
