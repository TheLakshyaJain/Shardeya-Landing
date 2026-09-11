package com.shardeya.foundation.auth.dto;

// Shared across signup, login-with-OTP, resend, and forgot-password's
// mobile branch -- maskedRecipient is a masked email for the first two
// (Email OTP fix) and a masked mobile for the last (unaffected, still SMS).
public record SignupResponse(String challengeId, String maskedRecipient, long resendAfterSeconds) {
}
