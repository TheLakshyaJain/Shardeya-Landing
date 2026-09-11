package com.shardeya.foundation.calculator.dto;

import java.math.BigDecimal;

public record UnitResponse(String code, String nameEn, String nameHi, BigDecimal toSqftFactor) {
}
