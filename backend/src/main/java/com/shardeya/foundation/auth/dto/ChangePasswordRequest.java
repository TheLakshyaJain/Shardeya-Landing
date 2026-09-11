package com.shardeya.foundation.auth.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

public record ChangePasswordRequest(
        @NotBlank(message = "error.auth.currentPasswordRequired") String currentPassword,

        @NotBlank(message = "error.password.weak")
        @Pattern(regexp = "^(?=.*[A-Za-z])(?=.*\\d).{8,}$", message = "error.password.weak")
        String newPassword
) {
}
