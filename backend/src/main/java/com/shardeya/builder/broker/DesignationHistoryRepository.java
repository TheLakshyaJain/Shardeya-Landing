package com.shardeya.builder.broker;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface DesignationHistoryRepository extends JpaRepository<DesignationHistory, UUID> {

    List<DesignationHistory> findByOrgIdAndBrokerIdOrderByEffectiveAtDesc(UUID orgId, UUID brokerId);

    /** §29's builder/admin overview -- every promotion/demotion/override across the whole network, newest first. */
    List<DesignationHistory> findByOrgIdOrderByEffectiveAtDesc(UUID orgId);
}
