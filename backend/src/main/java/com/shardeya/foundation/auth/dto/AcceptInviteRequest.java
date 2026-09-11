package com.shardeya.foundation.auth.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

/** B-12 §7: a staff member sets their own password via the single-use invite link -- credentials are never generated/sent in plaintext. */
public record AcceptInviteRequest(
        @NotBlank(message = "error.auth.inviteTokenInvalid")
        String token,

        @NotBlank(message = "error.password.weak")
        @Pattern(regexp = "^(?=.*[A-Za-z])(?=.*\\d).{8,}$", message = "error.password.weak")
        String password,

        @NotBlank(message = "error.password.mismatch")
        String confirmPassword
) {
}
