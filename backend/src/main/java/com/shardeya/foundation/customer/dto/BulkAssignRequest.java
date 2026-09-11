package com.shardeya.foundation.customer.dto;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

import java.util.List;
import java.util.UUID;

public record BulkAssignRequest(
        @NotEmpty(message = "error.customer.bulkAssignEmpty") List<UUID> customerIds,
        @NotNull(message = "error.customer.assigneeRequired") UUID userId
) {
}
