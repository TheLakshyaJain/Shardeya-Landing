package com.shardeya.builder.tracker.dto;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;

import java.util.List;
import java.util.UUID;

public record BulkRemindRequest(
        @NotEmpty(message = "error.tracker.scheduleIdsRequired")
        @Size(max = 200, message = "error.tracker.bulkRemindTooMany")
        List<UUID> scheduleIds
) {
}
