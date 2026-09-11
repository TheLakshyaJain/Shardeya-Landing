package com.shardeya.foundation.customer.dto;

import jakarta.validation.constraints.NotNull;

import java.util.UUID;

public record AssignRequest(@NotNull(message = "error.customer.assigneeRequired") UUID userId) {
}
