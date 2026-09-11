package com.shardeya.foundation.notification;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface MessageDeliveryRepository extends JpaRepository<MessageDelivery, UUID> {

    // RLS already scopes this to the caller's own org (V7_015's tenant_isolation
    // policy) -- orgId is still bound explicitly as a real @Param below, same
    // defense-in-depth reasoning as AppErrorLogRepository.findVisibleToOrg.
    List<MessageDelivery> findByOrgIdOrderByCreatedAtDesc(UUID orgId, Pageable pageable);

    // status bound as a real @Param, never an inlined enum literal in a
    // hand-written @Query string -- see OutboxEventRepository's own comment
    // (and the 4-times-recurring bug class CLAUDE.md tracks) for why a
    // derived-query method like this one is the safe shape here; Spring
    // Data generates the correct native-enum-typed parameter binding on its
    // own for a method like this, so no @Query is even needed.
    List<MessageDelivery> findByOrgIdAndStatusOrderByCreatedAtDesc(UUID orgId, MessageDelivery.Status status, Pageable pageable);
}
