package com.shardeya.foundation.admin.dto;

import java.time.Instant;
import java.util.UUID;

public record AppErrorLogRow(
        UUID id, Instant occurredAt, String httpMethod, String path, String exceptionClass, String message,
        boolean platformLevel
) {
}
