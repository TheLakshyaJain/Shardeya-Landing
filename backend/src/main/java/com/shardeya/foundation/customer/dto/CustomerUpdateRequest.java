package com.shardeya.foundation.customer.dto;

import com.shardeya.foundation.customer.Customer;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

public record CustomerUpdateRequest(
        String fullName,
        String mobile,
        String alternateMobile,
        String email,
        BigDecimal budgetMin,
        BigDecimal budgetMax,
        Customer.PreferredPropertyType preferredPropertyType,
        String preferredLocality,
        String sizeRequirement,
        Customer.Source source,
        UUID sourceBrokerId,
        UUID interestedProjectId,
        UUID interestedPlotId,
        LocalDate followUpDate,
        LocalDate siteVisitDate,
        Boolean noFurtherFollowUp,
        String remarks
) {
}
