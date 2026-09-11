package com.shardeya.foundation.calculator.dto;

import java.math.BigDecimal;

public record PlotSizeResponse(
        BigDecimal sqft, BigDecimal sqm, BigDecimal sqyd, BigDecimal bigha, String bighaStateApplied,
        BigDecimal gunta, BigDecimal dismil
) {
}
