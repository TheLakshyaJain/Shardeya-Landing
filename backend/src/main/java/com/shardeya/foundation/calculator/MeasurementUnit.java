package com.shardeya.foundation.calculator;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.util.UUID;

/** Platform-managed reference data (01-DATA-MODEL.md §2) — no org_id, no RLS, same class as `plan`/system `role` rows. */
@Entity
@Table(name = "measurement_unit")
public class MeasurementUnit {

    @Id
    private UUID id;

    @Column(nullable = false, length = 16)
    private String code;

    @Column(name = "name_en", nullable = false, length = 60)
    private String nameEn;

    @Column(name = "name_hi", nullable = false, length = 60)
    private String nameHi;

    @Column(name = "to_sqft_factor", nullable = false, precision = 14, scale = 6)
    private BigDecimal toSqftFactor;

    @Column(name = "state_code", length = 2)
    private String stateCode;

    @Column(name = "is_active", nullable = false)
    private boolean active;

    protected MeasurementUnit() {
    }

    public String getCode() {
        return code;
    }

    public String getNameEn() {
        return nameEn;
    }

    public String getNameHi() {
        return nameHi;
    }

    public BigDecimal getToSqftFactor() {
        return toSqftFactor;
    }

    public String getStateCode() {
        return stateCode;
    }

    public boolean isActive() {
        return active;
    }
}
