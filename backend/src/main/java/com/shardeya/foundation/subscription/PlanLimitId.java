package com.shardeya.foundation.subscription;

import java.io.Serializable;
import java.util.Objects;

public class PlanLimitId implements Serializable {

    private String planCode;
    private String limitKey;

    public PlanLimitId() {
    }

    public PlanLimitId(String planCode, String limitKey) {
        this.planCode = planCode;
        this.limitKey = limitKey;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof PlanLimitId that)) return false;
        return Objects.equals(planCode, that.planCode) && Objects.equals(limitKey, that.limitKey);
    }

    @Override
    public int hashCode() {
        return Objects.hash(planCode, limitKey);
    }
}
