package com.shardeya.foundation.auth.dto;

import jakarta.validation.constraints.NotBlank;

public record ForgotPasswordRequest(@NotBlank(message = "error.auth.identifierRequired") String identifier) {
}
