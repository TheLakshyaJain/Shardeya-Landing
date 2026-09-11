package com.shardeya.foundation.calculator;

import com.shardeya.platform.BadRequestException;
import com.shardeya.shared.AreaMeasure;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * CLAUDE.md rule #3: area is always dual-stored. This is the one place
 * (value, unit, stateCode) -> size_sqft happens — project/plot writes and the
 * Plot Size calculator all go through here so the conversion factor (and
 * Bigha's state-dependent definition) is never duplicated.
 */
@Service
public class AreaConversionService {

    private final MeasurementUnitRepository repository;

    public AreaConversionService(MeasurementUnitRepository repository) {
        this.repository = repository;
    }

    public AreaMeasure toSqft(BigDecimal value, String unitCode, String stateCode) {
        MeasurementUnit unit = repository.findBestMatch(unitCode, stateCode)
                .orElseThrow(() -> new BadRequestException("unit", "UNIT_NOT_FOUND", "error.area.unitNotFound"));
        BigDecimal sqft = value.multiply(unit.getToSqftFactor()).setScale(4, RoundingMode.HALF_UP);
        return new AreaMeasure(value, unitCode, sqft);
    }

    /** Reverse direction — used by the Plot Size calculator's multi-unit result card. */
    public BigDecimal fromSqft(BigDecimal sqft, String unitCode, String stateCode) {
        MeasurementUnit unit = repository.findBestMatch(unitCode, stateCode)
                .orElseThrow(() -> new BadRequestException("unit", "UNIT_NOT_FOUND", "error.area.unitNotFound"));
        return sqft.divide(unit.getToSqftFactor(), 4, RoundingMode.HALF_UP);
    }

    public MeasurementUnit resolveBigha(String stateCode) {
        return repository.findBestMatch("BIGHA", stateCode).orElse(null);
    }
}
