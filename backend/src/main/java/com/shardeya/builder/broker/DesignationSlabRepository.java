package com.shardeya.builder.broker;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.UUID;

public interface DesignationSlabRepository extends JpaRepository<DesignationSlab, UUID> {

    /**
     * Every slab visible to this org (its own, if any exist, plus the
     * system defaults), ordered so an org-specific row always outranks a
     * system-default row covering the same team-sales count -- mirrors
     * CommissionConfigService's own PLOT -> PROJECT -> GLOBAL precedence
     * pattern (more specific wins), here it's ORG-SPECIFIC -> SYSTEM-DEFAULT.
     */
    @Query("""
            SELECT s FROM DesignationSlab s
            WHERE (s.orgId = :orgId OR s.orgId IS NULL) AND s.deletedAt IS NULL AND s.active = true
              AND :teamSales >= s.minTeamSales AND (s.maxTeamSales IS NULL OR :teamSales <= s.maxTeamSales)
            ORDER BY (CASE WHEN s.orgId IS NULL THEN 1 ELSE 0 END) ASC, s.minTeamSales DESC
            """)
    List<DesignationSlab> findMatchingSlabs(@Param("orgId") UUID orgId, @Param("teamSales") int teamSales);

    @Query("""
            SELECT s FROM DesignationSlab s
            WHERE (s.orgId = :orgId OR s.orgId IS NULL) AND s.deletedAt IS NULL AND s.active = true
            ORDER BY (CASE WHEN s.orgId IS NULL THEN 1 ELSE 0 END) ASC, s.sortOrder ASC
            """)
    List<DesignationSlab> findAllForOrg(@Param("orgId") UUID orgId);

    // §7's revised same-slab formula: "the rate of the slab immediately
    // above U's current slab" -- resolved from the same designation_slab
    // config every other slab lookup in this engine already reads, never a
    // second hardcoded ladder. Ordered ascending so the caller's own
    // first-element pick is the CLOSEST slab above, not just any higher
    // one -- returns empty for the top slab (no fallback, no fabricated
    // row; DesignationSlabService.resolveNextRate() turns that into the
    // ₹0 case).
    @Query("""
            SELECT s FROM DesignationSlab s
            WHERE (s.orgId = :orgId OR s.orgId IS NULL) AND s.deletedAt IS NULL AND s.active = true
              AND s.sortOrder > :currentSortOrder
            ORDER BY s.sortOrder ASC
            """)
    List<DesignationSlab> findSlabsAboveSortOrder(@Param("orgId") UUID orgId, @Param("currentSortOrder") short currentSortOrder);

    @Query("""
            SELECT s FROM DesignationSlab s
            WHERE (s.orgId = :orgId OR s.orgId IS NULL) AND s.deletedAt IS NULL AND lower(s.name) = lower(:name)
            ORDER BY (CASE WHEN s.orgId IS NULL THEN 1 ELSE 0 END) ASC
            """)
    List<DesignationSlab> findByNameForOrg(@Param("orgId") UUID orgId, @Param("name") String name);
}
