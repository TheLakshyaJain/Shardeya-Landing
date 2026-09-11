package com.shardeya.builder.payment.dto;

import java.math.BigDecimal;
import java.time.LocalDate;

public record ScheduleUpdateRequest(String label, BigDecimal amount, LocalDate dueDate) {
}
