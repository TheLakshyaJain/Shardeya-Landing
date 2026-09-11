package com.shardeya.builder.sale.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.LocalDate;

public record ScheduleRowRequest(
        @Size(max = 80, message = "error.schedule.labelInvalid") String label,
        @NotNull(message = "error.schedule.amountRequired") @Positive(message = "error.schedule.amountInvalid") BigDecimal amount,
        @NotNull(message = "error.schedule.dueDateRequired") LocalDate dueDate
) {
}
