package com.shardeya.builder.dashboard;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface OrgMetricsRepository extends JpaRepository<OrgMetrics, UUID> {

    Optional<OrgMetrics> findByOrgId(UUID orgId);
}
