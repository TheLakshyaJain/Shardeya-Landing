package com.shardeya.foundation.subscription;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;
import java.util.UUID;

public interface OrgUsageRepository extends JpaRepository<OrgUsage, UUID> {

    @Query("""
            SELECT u FROM OrgUsage u WHERE u.orgId = :orgId AND u.limitKey = :limitKey
              AND ((:scopeId IS NULL AND u.scopeId IS NULL) OR u.scopeId = :scopeId)
            """)
    Optional<OrgUsage> find(@Param("orgId") UUID orgId, @Param("limitKey") String limitKey, @Param("scopeId") UUID scopeId);

    // Postgres advisory lock, held for the rest of the caller's transaction
    // (pg_advisory_xact_lock auto-releases at commit/rollback, no manual
    // unlock needed) — serializes concurrent quota checks for the same
    // (org, limit, scope) key even on an org's very FIRST project/plot, when
    // no org_usage row exists yet to take a row lock (SELECT ... FOR UPDATE)
    // on. Without this, two simultaneous "create project" requests against a
    // brand-new org could both read current_value=0 against a limit of 1 and
    // both succeed.
    @Query(value = "SELECT pg_advisory_xact_lock(hashtext(:key))", nativeQuery = true)
    void advisoryLock(@Param("key") String key);
}
