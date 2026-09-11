package com.shardeya.builder.broker.dto;

import com.shardeya.builder.broker.BrokerPartner;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * B-14 §20.2/§11 -- all 16 original fields the broker form collects, plus
 * {@code uplineBrokerId} from 06-BROKER-NETWORK-ENGINE.md §30/§31 --
 * DESIGNATION only, explicitly selected, never inferred from the adding
 * user's own upline chain.
 */
public record BrokerCreateRequest(
        @NotBlank(message = "error.broker.fullNameRequired") @Size(min = 2, max = 120, message = "error.broker.fullNameInvalid") String fullName,
        @NotBlank(message = "error.broker.mobileRequired") @Pattern(regexp = "^[6-9]\\d{9}$", message = "error.broker.mobileInvalid") String mobile,
        @Email(message = "error.broker.emailInvalid") String email,
        @Size(max = 150, message = "error.broker.cityAreaInvalid") String cityArea,
        @Size(max = 60, message = "error.broker.reraNumberInvalid") String reraNumber,
        @Size(max = 150, message = "error.broker.firmNameInvalid") String firmName,
        @NotNull(message = "error.broker.commissionTypeRequired") BrokerPartner.CommissionType commissionType,
        @DecimalMin(value = "0", message = "error.broker.commissionPctInvalid") @DecimalMax(value = "20", message = "error.broker.commissionPctInvalid") BigDecimal commissionPct,
        @DecimalMin(value = "0.01", message = "error.broker.commissionFixedInvalid") @DecimalMax(value = "10000000", message = "error.broker.commissionFixedInvalid") BigDecimal commissionFixed,
        boolean perProjectRatesEnabled,
        @Size(max = 150) String bankAccountName,
        String bankAccountNumber,
        @Pattern(regexp = "^[A-Z]{4}0[A-Z0-9]{6}$", message = "error.broker.ifscInvalid") String ifsc,
        @Pattern(regexp = "^[\\w.\\-]{2,}@[a-zA-Z]{2,}$", message = "error.broker.upiInvalid") String upiId,
        String notes,
        UUID uplineBrokerId
) {
}
