package com.shardeya.builder.project.dto;

import com.shardeya.builder.project.Project;
import jakarta.validation.constraints.NotNull;

public record ProjectStatusUpdateRequest(@NotNull(message = "error.project.statusRequired") Project.Status status) {
}
