package com.shardeya.builder.broker.dto;

import com.shardeya.builder.broker.BrokerPartner;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

public record BrokerUpdateRequest(
        @Size(min = 2, max = 120, message = "error.broker.fullNameInvalid") String fullName,
        @Pattern(regexp = "^[6-9]\\d{9}$", message = "error.broker.mobileInvalid") String mobile,
        @Email(message = "error.broker.emailInvalid") String email,
        @Size(max = 150) String cityArea,
        @Size(max = 60) String reraNumber,
        @Size(max = 150) String firmName,
        BrokerPartner.CommissionType commissionType,
        @DecimalMin(value = "0") @DecimalMax(value = "20") BigDecimal commissionPct,
        @DecimalMin(value = "0.01") @DecimalMax(value = "10000000") BigDecimal commissionFixed,
        Boolean perProjectRatesEnabled,
        @Size(max = 150) String bankAccountName,
        String bankAccountNumber,
        @Pattern(regexp = "^[A-Z]{4}0[A-Z0-9]{6}$", message = "error.broker.ifscInvalid") String ifsc,
        @Pattern(regexp = "^[\\w.\\-]{2,}@[a-zA-Z]{2,}$", message = "error.broker.upiInvalid") String upiId,
        String notes
) {
}
