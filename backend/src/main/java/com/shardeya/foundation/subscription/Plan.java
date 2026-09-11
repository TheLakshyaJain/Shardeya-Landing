package com.shardeya.foundation.subscription;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.math.BigDecimal;

/**
 * M1 scope note: only FREE is seeded (V1_007). PRO/PREMIUM real pricing isn't
 * defined anywhere in 00-05 — that's an M-09 (Subscription Payments) concern.
 */
@Entity
@Table(name = "plan")
public class Plan {

    @Id
    private String code;

    @Column(name = "name_en", nullable = false, length = 60)
    private String nameEn;

    @Column(name = "name_hi", nullable = false, length = 60)
    private String nameHi;

    @Column(name = "price_monthly", nullable = false, precision = 19, scale = 2)
    private BigDecimal priceMonthly;

    @Column(name = "price_yearly", nullable = false, precision = 19, scale = 2)
    private BigDecimal priceYearly;

    @Column(name = "sort_order", nullable = false)
    private short sortOrder;

    @Column(name = "is_active", nullable = false)
    private boolean active;

    protected Plan() {
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
}
