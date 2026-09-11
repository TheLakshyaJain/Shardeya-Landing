package com.shardeya.builder.team;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface StaffInviteRepository extends JpaRepository<StaffInvite, UUID> {

    Optional<StaffInvite> findFirstByAppUserIdAndDeletedAtIsNullOrderByCreatedAtDesc(UUID appUserId);
}
