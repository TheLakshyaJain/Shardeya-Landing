package com.shardeya.foundation.customer.dto;

import com.shardeya.foundation.customer.Customer;
import jakarta.validation.constraints.NotNull;

public record StatusUpdateRequest(@NotNull(message = "error.customer.statusRequired") Customer.Status status, String note) {
}
