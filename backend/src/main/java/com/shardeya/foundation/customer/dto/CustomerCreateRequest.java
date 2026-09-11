package com.shardeya.foundation.customer.dto;

import com.shardeya.foundation.customer.Customer;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

/** Field validations mirror 02-FOUNDATION-MODULES.md M-12 §11. */
public record CustomerCreateRequest(
        @NotBlank(message = "error.customer.fullNameRequired")
        @Size(min = 2, max = 120, message = "error.customer.fullNameInvalid")
        String fullName,

        @NotBlank(message = "error.customer.mobileRequired")
        @Pattern(regexp = "^[6-9]\\d{9}$", message = "error.customer.mobileInvalid")
        String mobile,

        String alternateMobile,
        String email,

        @NotNull(message = "error.customer.budgetRequired")
        @DecimalMin(value = "0.01", message = "error.customer.budgetInvalid")
        BigDecimal budgetMin,

        @NotNull(message = "error.customer.budgetRequired")
        @DecimalMin(value = "0.01", message = "error.customer.budgetInvalid")
        BigDecimal budgetMax,

        Customer.PreferredPropertyType preferredPropertyType,
        String preferredLocality,
        String sizeRequirement,

        @NotNull(message = "error.customer.sourceRequired")
        Customer.Source source,

        UUID sourceBrokerId,
        Customer.Status status,
        UUID interestedProjectId,
        UUID interestedPlotId,

        LocalDate followUpDate,
        LocalDate siteVisitDate,
        String remarks,

        /** If false/omitted, the creating user is auto-assigned (B-07 §8: "auto-assigned to the creating exec"). */
        UUID assignedTo,

        /** Create-anyway path for M-12 §7 duplicate detection -- when true, skips the existing-mobile check. */
        boolean allowDuplicate
) {
}
