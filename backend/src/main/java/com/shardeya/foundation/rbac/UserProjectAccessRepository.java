package com.shardeya.foundation.rbac;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface UserProjectAccessRepository extends JpaRepository<UserProjectAccess, UUID> {

    List<UserProjectAccess> findByOrgIdAndUserId(UUID orgId, UUID userId);

    void deleteByOrgIdAndUserId(UUID orgId, UUID userId);
}
