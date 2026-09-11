package com.shardeya.foundation.calculator.dto;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;

/** M-08 §11: dealValue > 0, <= 10,000 crore; brokeragePct 0-100, max 3 decimals; share pcts 0-100. */
public record BrokerageCalcRequest(
        @NotNull(message = "error.calc.dealValueRequired") @DecimalMin(value = "0.01", message = "error.calc.dealValueInvalid")
        @DecimalMax(value = "100000000000", message = "error.calc.dealValueInvalid") BigDecimal dealValue,

        @NotNull(message = "error.calc.brokeragePctRequired") @DecimalMin(value = "0", message = "error.calc.brokeragePctInvalid")
        @DecimalMax(value = "100", message = "error.calc.brokeragePctInvalid") BigDecimal brokeragePct,

        @DecimalMin(value = "0", message = "error.calc.sharePctInvalid") @DecimalMax(value = "100", message = "error.calc.sharePctInvalid") BigDecimal ownerSharePct,
        @DecimalMin(value = "0", message = "error.calc.sharePctInvalid") @DecimalMax(value = "100", message = "error.calc.sharePctInvalid") BigDecimal buyerSharePct
) {
}
