package com.shardeya.builder.broker;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface BrokerTierRepository extends JpaRepository<BrokerTier, UUID> {

    Optional<BrokerTier> findByIdAndOrgIdAndDeletedAtIsNull(UUID id, UUID orgId);

    List<BrokerTier> findByOrgIdAndDeletedAtIsNullOrderBySortOrderAsc(UUID orgId);

    @Query("""
            SELECT t FROM BrokerTier t
            WHERE t.orgId = :orgId AND t.deletedAt IS NULL AND t.active = true
              AND :dealsCount >= t.minDeals AND (t.maxDeals IS NULL OR :dealsCount <= t.maxDeals)
            ORDER BY t.minDeals DESC
            """)
    List<BrokerTier> findMatchingTiers(@Param("orgId") UUID orgId, @Param("dealsCount") int dealsCount);

    @Query("SELECT COUNT(t) > 0 FROM BrokerTier t WHERE t.orgId = :orgId AND t.deletedAt IS NULL AND LOWER(t.name) = LOWER(:name)")
    boolean existsByOrgIdAndNameIgnoreCase(@Param("orgId") UUID orgId, @Param("name") String name);
}
