package com.shardeya.builder.broker.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.UUID;

/** 06-BROKER-NETWORK-ENGINE.md §34, build-order step 8 -- mirrors TierOverrideRequest (M6) exactly. */
public record DesignationOverrideRequest(
        @NotNull(message = "error.designation.designationIdRequired") UUID designationId,
        @NotBlank(message = "error.designation.reasonRequired") @Size(min = 5, max = 500, message = "error.designation.reasonInvalid") String reason
) {
}
