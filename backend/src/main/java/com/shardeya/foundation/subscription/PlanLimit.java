package com.shardeya.foundation.subscription;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.Table;

@Entity
@Table(name = "plan_limit")
@IdClass(PlanLimitId.class)
public class PlanLimit {

    @Id
    @Column(name = "plan_code", length = 20)
    private String planCode;

    @Id
    @Column(name = "limit_key", length = 40)
    private String limitKey;

    @Column(name = "limit_value", nullable = false, length = 20)
    private String limitValue;

    protected PlanLimit() {
    }

    public String getPlanCode() {
        return planCode;
    }

    public String getLimitKey() {
        return limitKey;
    }

    public String getLimitValue() {
        return limitValue;
    }

    /** {@code -1} means unlimited (01-DATA-MODEL.md §10). */
    public int asInt() {
        return Integer.parseInt(limitValue);
    }

    public boolean isUnlimited() {
        return "-1".equals(limitValue);
    }
}
