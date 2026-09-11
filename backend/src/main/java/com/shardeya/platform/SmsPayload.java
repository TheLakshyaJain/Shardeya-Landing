package com.shardeya.platform;

import java.util.UUID;

/**
 * {@link OutboxEvent#getPayload()} shape for {@code event_type = "SMS"}
 * events (M-06 second half). Distinct from the existing, direct, synchronous
 * {@code SmsGateway.sendOtp}/{@code sendText} calls used for OTP and staff
 * invite links -- those stay synchronous on purpose (a login/signup flow
 * can't wait on the outbox poller's 2s tick), this is for everything else:
 * notification-preference-driven sends and the critical-type WhatsApp
 * fallback.
 */
public record SmsPayload(UUID orgId, String mobile, String text, String purpose) {
}
