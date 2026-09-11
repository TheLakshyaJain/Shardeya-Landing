package com.shardeya.foundation.notification.dto;

public record WhatsAppOptInChallengeResponse(String challengeId, String maskedMobile, long resendAfterSeconds) {
}
