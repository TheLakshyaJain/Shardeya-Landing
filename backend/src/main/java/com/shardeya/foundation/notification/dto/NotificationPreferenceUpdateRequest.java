package com.shardeya.foundation.notification.dto;

import jakarta.validation.constraints.NotBlank;

public record NotificationPreferenceUpdateRequest(@NotBlank String typeCode, boolean inApp, boolean whatsapp,
                                                    boolean sms, boolean email) {
}
