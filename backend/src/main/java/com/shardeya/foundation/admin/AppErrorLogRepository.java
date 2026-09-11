package com.shardeya.foundation.admin;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.UUID;

public interface AppErrorLogRepository extends JpaRepository<AppErrorLog, UUID> {

    // RLS already restricts this to rows the caller's org can see (own org
    // + platform-level org_id IS NULL rows) -- this WHERE clause mirrors
    // the policy as an explicit, defense-in-depth predicate (CLAUDE.md rule
    // #1: "RLS is the backstop, not the primary mechanism"), not a
    // second, independent access-control decision.
    @Query("SELECT e FROM AppErrorLog e WHERE e.orgId = :orgId OR e.orgId IS NULL ORDER BY e.occurredAt DESC")
    List<AppErrorLog> findVisibleToOrg(@Param("orgId") UUID orgId, Pageable pageable);
}
