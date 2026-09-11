package com.shardeya.foundation.auth;

import java.time.Instant;

/**
 * Redis-serialized OTP challenge state. Redis is the sole store — see
 * CLAUDE.md "Milestone 1" notes for why there's no otp_challenge DB audit
 * table in M1.
 *
 * <p>{@code recipient} + {@code channel} (renamed from a mobile-only
 * {@code mobile} field): this codebase's OTP challenges are no longer
 * exclusively SMS-delivered (signup verification and "login with OTP" both
 * moved to email — see CLAUDE.md's "Email OTP" writeup for why), so the
 * stored recipient and its delivery channel need to travel together —
 * {@link OtpService#resend} in particular has no other way to know which
 * gateway to resend through.
 */
public record OtpChallengeData(
        String challengeId,
        String purpose,
        OtpService.Channel channel,
        String recipient,
        String codeHash,
        int attempts,
        int maxAttempts,
        Instant createdAt,
        Instant lastResendAt,
        int resendCount,
        String payloadJson) {

    public OtpChallengeData withCode(String newCodeHash, Instant now) {
        return new OtpChallengeData(challengeId, purpose, channel, recipient, newCodeHash, 0, maxAttempts,
                createdAt, now, resendCount + 1, payloadJson);
    }

    public OtpChallengeData withAttemptIncremented() {
        return new OtpChallengeData(challengeId, purpose, channel, recipient, codeHash, attempts + 1, maxAttempts,
                createdAt, lastResendAt, resendCount, payloadJson);
    }
}
