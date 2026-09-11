package com.shardeya.foundation.customer.dto;

import com.shardeya.foundation.customer.Interaction;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

public record InteractionResponse(
        UUID id,
        UUID customerId,
        UUID propertyId,
        UUID projectId,
        UUID plotId,
        LocalDate occurredOn,
        Interaction.Type type,
        String remarks,
        LocalDate nextFollowUpDate,
        Interaction.Result result,
        UUID conductedBy,
        Instant amendedAt,
        UUID amendedBy,
        boolean amendable,
        Instant createdAt
) {
    public static InteractionResponse from(Interaction i, boolean amendable) {
        return new InteractionResponse(i.getId(), i.getCustomerId(), i.getPropertyId(), i.getProjectId(),
                i.getPlotId(), i.getOccurredOn(), i.getType(), i.getRemarks(), i.getNextFollowUpDate(), i.getResult(),
                i.getConductedBy(), i.getAmendedAt(), i.getAmendedBy(), amendable, i.getCreatedAt());
    }
}
