package com.shardeya.foundation.customer;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface CustomerRepository extends JpaRepository<Customer, UUID>, JpaSpecificationExecutor<Customer> {

    Optional<Customer> findByIdAndOrgIdAndDeletedAtIsNull(UUID id, UUID orgId);

    boolean existsByOrgIdAndMobileAndDeletedAtIsNull(UUID orgId, String mobile);

    Optional<Customer> findFirstByOrgIdAndMobileAndDeletedAtIsNull(UUID orgId, String mobile);

    @Query("""
            SELECT c FROM Customer c
            WHERE c.orgId = :orgId AND c.deletedAt IS NULL AND c.assignedTo IS NULL
              AND c.status NOT IN :terminalStatuses
            ORDER BY c.createdAt DESC
            """)
    List<Customer> findUnassigned(@Param("orgId") UUID orgId, @Param("terminalStatuses") List<Customer.Status> terminalStatuses,
                                   Pageable pageable);

    // Leads whose interested plot was just sold, for the "flag alternatives"
    // edge case (B-07 §10) -- called from PlotSaleService right after a sale
    // commits. Enum literal bound as @Param, not inlined into the JPQL string
    // -- CLAUDE.md's M3 notes document this exact bug class (Hibernate
    // mangles an inline enum literal's cast using the Java simple name
    // instead of the real Postgres enum type) recurring twice already; this
    // is deliberately not a third repeat.
    @Query("""
            SELECT c FROM Customer c
            WHERE c.orgId = :orgId AND c.deletedAt IS NULL AND c.interestedPlotId = :plotId
              AND c.status NOT IN :terminalStatuses
            """)
    List<Customer> findActiveInterestedInPlot(@Param("orgId") UUID orgId, @Param("plotId") UUID plotId,
                                               @Param("terminalStatuses") List<Customer.Status> terminalStatuses);

    @Query("""
            SELECT count(c) FROM Customer c
            WHERE c.orgId = :orgId AND c.deletedAt IS NULL
              AND (:projectId IS NULL OR c.interestedProjectId = :projectId)
              AND c.status = :status
            """)
    long countByStatus(@Param("orgId") UUID orgId, @Param("projectId") UUID projectId,
                        @Param("status") Customer.Status status);

    @Query("""
            SELECT c FROM Customer c
            WHERE c.orgId = :orgId AND c.deletedAt IS NULL AND c.followUpDate = :date
              AND c.noFurtherFollowUp = false
              AND c.status NOT IN :terminalStatuses
            """)
    List<Customer> findDueForFollowUp(@Param("orgId") UUID orgId, @Param("date") LocalDate date,
                                       @Param("terminalStatuses") List<Customer.Status> terminalStatuses);
}
