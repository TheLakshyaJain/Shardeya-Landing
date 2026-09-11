package com.shardeya.foundation.auth.dto;

import jakarta.validation.constraints.NotBlank;

public record OtpResendRequest(@NotBlank(message = "error.otp.invalid") String challengeId) {
}
