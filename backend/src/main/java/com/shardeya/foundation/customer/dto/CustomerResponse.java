package com.shardeya.foundation.customer.dto;

import com.shardeya.foundation.customer.Customer;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

public record CustomerResponse(
        UUID id,
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
        Customer.Status status,
        UUID interestedProjectId,
        UUID interestedPlotId,
        UUID assignedTo,
        LocalDate followUpDate,
        LocalDate siteVisitDate,
        boolean noFurtherFollowUp,
        boolean important,
        String remarks,
        Instant lastInteractionAt,
        LocalDate closedAt,
        Instant createdAt,
        UUID createdBy
) {
    public static CustomerResponse from(Customer c) {
        return new CustomerResponse(c.getId(), c.getFullName(), c.getMobile(), c.getAlternateMobile(), c.getEmail(),
                c.getBudgetMin(), c.getBudgetMax(), c.getPreferredPropertyType(), c.getPreferredLocality(),
                c.getSizeRequirement(), c.getSource(), c.getSourceBrokerId(), c.getStatus(),
                c.getInterestedProjectId(), c.getInterestedPlotId(), c.getAssignedTo(), c.getFollowUpDate(),
                c.getSiteVisitDate(), c.isNoFurtherFollowUp(), c.isImportant(), c.getRemarks(),
                c.getLastInteractionAt(), c.getClosedAt(), c.getCreatedAt(), c.getCreatedBy());
    }
}
