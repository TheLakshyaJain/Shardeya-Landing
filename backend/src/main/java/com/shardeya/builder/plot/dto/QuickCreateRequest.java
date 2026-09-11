package com.shardeya.builder.plot.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

import java.util.List;

public record QuickCreateRequest(
        @NotEmpty(message = "error.plot.quickCreate.rangesRequired")
        @Valid
        List<QuickCreateRangeRequest> ranges,

        @NotNull(message = "error.plot.quickCreate.sharedPropertiesRequired")
        @Valid
        QuickCreateSharedPropertiesRequest sharedProperties,

        boolean autoPlace
) {
}
