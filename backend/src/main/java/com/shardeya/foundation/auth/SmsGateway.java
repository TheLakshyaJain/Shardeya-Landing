package com.shardeya.foundation.auth;

/**
 * Provider-agnostic on purpose (CLAUDE.md instruction: "design the adapter
 * interface so a real provider can be plugged in later via config alone").
 * {@code shardeya.sms.provider} selects the implementation bean; only
 * {@code stub} exists for now — no MSG91/Gupshup account to integrate yet.
 */
public interface SmsGateway {

    void sendOtp(String mobile, String code, String purpose);

    /** M4: staff-invite set-password links (B-12 §7) -- the one non-OTP SMS this project sends; everything else stays M7 scope per V3_007's own comment. */
    void sendText(String mobile, String text, String purpose);
}
