package com.shardeya.builder.plot.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * B-06 Path A: one numeric range. {@code {prefix:"A", separator:"-", start:1,
 * end:75}} generates {@code A-1 … A-75}; no prefix generates bare {@code 1 …
 * 200}; {@code padWidth:3} generates {@code A-001 … A-075}.
 */
public record QuickCreateRangeRequest(
        @Size(max = 10, message = "error.plot.quickCreate.prefixTooLong")
        String prefix,

        String separator,

        @NotNull(message = "error.plot.quickCreate.startRequired")
        @Min(value = 1, message = "error.plot.quickCreate.startInvalid")
        Integer start,

        @NotNull(message = "error.plot.quickCreate.endRequired")
        Integer end,

        @Min(value = 0, message = "error.plot.quickCreate.padWidthInvalid")
        @Max(value = 6, message = "error.plot.quickCreate.padWidthInvalid")
        Integer padWidth
) {
}
