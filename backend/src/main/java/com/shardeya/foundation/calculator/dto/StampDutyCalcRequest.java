package com.shardeya.foundation.calculator.dto;

import com.shardeya.foundation.calculator.StampDutyRate;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

public record StampDutyCalcRequest(
        @NotBlank(message = "error.calc.stateCodeRequired") @Size(min = 2, max = 2, message = "error.calc.stateCodeInvalid") String stateCode,
        @NotNull(message = "error.calc.propertyTypeRequired") StampDutyRate.PropertyType propertyType,
        @NotNull(message = "error.calc.transactionTypeRequired") StampDutyRate.TransactionType transactionType,
        @NotNull(message = "error.calc.valueRequired") @DecimalMin(value = "0.01", message = "error.calc.valueInvalid") BigDecimal value,
        @NotNull(message = "error.calc.buyerGenderRequired") StampDutyRate.BuyerGender buyerGender
) {
}
