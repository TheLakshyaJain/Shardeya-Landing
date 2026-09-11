package com.shardeya.foundation.auth;

/**
 * Signup's OTP challenge payload (M-01 §7: "the payload lives in Redis under
 * challengeId... before verification"). Holds a hash, never the plaintext
 * password — minimizes what sits in Redis even for the 15-minute window.
 */
public record PendingSignup(
        String fullName, String mobile, String email, String passwordHash, Organization.Type role, String city) {
}
