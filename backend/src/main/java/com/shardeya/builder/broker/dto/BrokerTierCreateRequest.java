package com.shardeya.builder.broker.dto;

import com.shardeya.builder.broker.BrokerTier;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

public record BrokerTierCreateRequest(
        @NotBlank(message = "error.brokerTier.nameRequired") @Size(min = 2, max = 40, message = "error.brokerTier.nameInvalid") String name,
        @Size(max = 40) String nameHi,
        @NotNull(message = "error.brokerTier.minDealsRequired") @Min(value = 0, message = "error.brokerTier.minDealsInvalid") Integer minDeals,
        Integer maxDeals,
        BrokerTier.BonusType bonusType,
        @DecimalMin(value = "0", message = "error.brokerTier.bonusValueInvalid") BigDecimal bonusValue,
        String perksDescription
) {
}
