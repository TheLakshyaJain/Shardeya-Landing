package com.shardeya.foundation.auth.dto;

import jakarta.validation.constraints.NotBlank;

// Flexible mobile-or-email, exactly like LoginRequest.identifier -- not
// email-only. That's what makes a resolved account with no email on file
// (M4 team members: mobile required, email optional) an actual, reachable
// case AuthService.requestLoginOtp can give a clear answer to, instead of
// email-only input making "no email on file" indistinguishable from "wrong
// email typed."
public record LoginOtpRequestRequest(
        @NotBlank(message = "error.auth.identifierRequired") String identifier
) {
}
