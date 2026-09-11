package com.shardeya.foundation.auth.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

/**
 * Exactly one of (token) or (challengeId + code) must be present — token for
 * the email "signed link" path, challengeId+code for the mobile OTP path
 * (M-01 §8: "Forgot → identifier → (mobile: OTP | email: signed link, 30 min,
 * single-use)"). Checked in AuthService, not here — Bean Validation doesn't
 * have a clean "exactly one of A or (B and C)" primitive.
 */
public record ResetPasswordRequest(
        String token,
        String challengeId,
        @Pattern(regexp = "^\\d{6}$", message = "error.otp.invalid") String code,

        @NotBlank(message = "error.password.weak")
        @Pattern(regexp = "^(?=.*[A-Za-z])(?=.*\\d).{8,}$", message = "error.password.weak")
        String newPassword,

        @NotBlank(message = "error.password.mismatch")
        String confirmPassword
) {
}
