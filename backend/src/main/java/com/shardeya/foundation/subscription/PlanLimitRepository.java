package com.shardeya.foundation.subscription;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface PlanLimitRepository extends JpaRepository<PlanLimit, PlanLimitId> {

    Optional<PlanLimit> findByPlanCodeAndLimitKey(String planCode, String limitKey);
}
