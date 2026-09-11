package com.shardeya.builder.project;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ProjectRepository extends JpaRepository<Project, UUID> {

    boolean existsByOrgIdAndDeletedAtIsNullAndNameIgnoreCase(UUID orgId, String name);

    Optional<Project> findByIdAndDeletedAtIsNull(UUID id);

    // Split into two queries rather than one "(:cursorCreatedAt IS NULL OR ...)"
    // query: when cursorCreatedAt is bound as a genuine null (every first-page
    // request, since there's no cursor yet), a parameter whose only use is in
    // an IS NULL check gives Postgres's JDBC driver no type to infer, and it
    // fails outright with "ERROR: could not determine data type of parameter
    // $2" -- not a corner case, this hit every single first load of the
    // Projects list. Confirmed via a real browser E2E run, not visible from
    // any unit/ArchUnit test. findFirstPage has no such parameter at all;
    // findPageAfter's cursor params are only ever bound to real, non-null
    // values, so Postgres can always infer their type from context.
    @Query("""
            SELECT p FROM Project p
            WHERE p.orgId = :orgId AND p.deletedAt IS NULL
            ORDER BY p.createdAt DESC, p.id DESC
            """)
    List<Project> findFirstPage(@Param("orgId") UUID orgId, Pageable pageable);

    @Query("""
            SELECT p FROM Project p
            WHERE p.orgId = :orgId AND p.deletedAt IS NULL
              AND (p.createdAt < :cursorCreatedAt
                   OR (p.createdAt = :cursorCreatedAt AND p.id < :cursorId))
            ORDER BY p.createdAt DESC, p.id DESC
            """)
    List<Project> findPageAfter(@Param("orgId") UUID orgId, @Param("cursorCreatedAt") Instant cursorCreatedAt,
                                 @Param("cursorId") UUID cursorId, Pageable pageable);

    // M4: project-scoped staff (app_user.project_access_mode = SCOPED) variants
    // of the two queries above -- same first-page/after-cursor split, same
    // reason (a null cursor param used only in an IS NULL check breaks
    // Postgres's type inference). Only called when
    // !TenantContext.current().allProjects().
    @Query("""
            SELECT p FROM Project p
            WHERE p.orgId = :orgId AND p.deletedAt IS NULL AND p.id IN :scope
            ORDER BY p.createdAt DESC, p.id DESC
            """)
    List<Project> findFirstPageScoped(@Param("orgId") UUID orgId, @Param("scope") List<UUID> scope, Pageable pageable);

    @Query("""
            SELECT p FROM Project p
            WHERE p.orgId = :orgId AND p.deletedAt IS NULL AND p.id IN :scope
              AND (p.createdAt < :cursorCreatedAt
                   OR (p.createdAt = :cursorCreatedAt AND p.id < :cursorId))
            ORDER BY p.createdAt DESC, p.id DESC
            """)
    List<Project> findPageAfterScoped(@Param("orgId") UUID orgId, @Param("cursorCreatedAt") Instant cursorCreatedAt,
                                       @Param("cursorId") UUID cursorId, @Param("scope") List<UUID> scope, Pageable pageable);
}
