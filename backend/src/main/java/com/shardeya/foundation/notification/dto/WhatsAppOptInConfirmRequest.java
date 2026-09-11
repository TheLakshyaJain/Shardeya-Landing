package com.shardeya.foundation.notification.dto;

import jakarta.validation.constraints.NotBlank;

public record WhatsAppOptInConfirmRequest(@NotBlank String challengeId, @NotBlank String code) {
}
