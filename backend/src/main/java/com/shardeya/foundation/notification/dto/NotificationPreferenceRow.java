package com.shardeya.foundation.notification.dto;

/** One row of the M-06 §22 preference matrix (M-04's own naming: "rows = event types grouped by category, columns = 4 channel toggles, mandatory ones locked"). Reflects EFFECTIVE values -- a user override if one exists, else the type's own defaults. */
public record NotificationPreferenceRow(String typeCode, String category, boolean mandatory,
                                         boolean inApp, boolean whatsapp, boolean sms, boolean email) {
}
