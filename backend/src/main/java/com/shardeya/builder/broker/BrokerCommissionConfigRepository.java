package com.shardeya.builder.broker;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface BrokerCommissionConfigRepository extends JpaRepository<BrokerCommissionConfig, UUID> {

    Optional<BrokerCommissionConfig> findByIdAndOrgIdAndDeletedAtIsNull(UUID id, UUID orgId);

    List<BrokerCommissionConfig> findByOrgIdAndBrokerPartnerIdAndDeletedAtIsNullOrderByEffectiveFromDesc(UUID orgId, UUID brokerPartnerId);

    // The resolution algorithm's core query (B-14 §7): for a given broker
    // and scope/target, the config row effective as of saleDate, most
    // recent effective_from first so the caller just takes the first
    // result. Called once per scope (PLOT, then PROJECT, then GLOBAL) by
    // the service, which is simpler and more testable than one giant
    // CASE-based single query trying to express the whole precedence chain
    // in SQL at once.
    @Query("""
            SELECT c FROM BrokerCommissionConfig c
            WHERE c.orgId = :orgId AND c.brokerPartnerId = :brokerId AND c.deletedAt IS NULL
              AND c.scope = :scope
              AND (:projectId IS NULL OR c.projectId = :projectId)
              AND (:plotId IS NULL OR c.plotId = :plotId)
              AND c.effectiveFrom <= :saleDate
              AND (c.effectiveTo IS NULL OR c.effectiveTo >= :saleDate)
            ORDER BY c.effectiveFrom DESC
            """)
    List<BrokerCommissionConfig> findEffectiveConfigs(@Param("orgId") UUID orgId, @Param("brokerId") UUID brokerId,
                                                        @Param("scope") BrokerCommissionConfig.Scope scope,
                                                        @Param("projectId") UUID projectId, @Param("plotId") UUID plotId,
                                                        @Param("saleDate") LocalDate saleDate);
}
