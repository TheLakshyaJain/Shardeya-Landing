package com.shardeya.foundation.rbac;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface RoleRepository extends JpaRepository<Role, UUID> {

    Optional<Role> findByCodeAndOrgIdIsNull(String code);

    List<Role> findByOrgIdIsNullAndCodeStartingWith(String prefix);

    List<Role> findByOrgIdIsNullAndCodeIn(List<String> codes);
}
