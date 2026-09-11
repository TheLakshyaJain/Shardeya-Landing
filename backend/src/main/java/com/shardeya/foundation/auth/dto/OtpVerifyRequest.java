package com.shardeya.foundation.auth.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

public record OtpVerifyRequest(
        @NotBlank(message = "error.otp.invalid") String challengeId,
        @NotBlank(message = "error.otp.invalid")
        @Pattern(regexp = "^\\d{6}$", message = "error.otp.invalid")
        String code
) {
}
