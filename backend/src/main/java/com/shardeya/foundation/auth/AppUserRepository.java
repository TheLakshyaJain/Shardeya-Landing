package com.shardeya.foundation.auth;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface AppUserRepository extends JpaRepository<AppUser, UUID> {

    // M3: NotificationService fans an org-wide notification out to every
    // active user in the org (e.g. "plot sold"). Only ever the single owner
    // today since staff roles don't exist until M4, but written to already
    // handle a multi-user org correctly once they do.
    List<AppUser> findByOrgIdAndDeletedAtIsNull(UUID orgId);

    Optional<AppUser> findByIdAndOrgIdAndDeletedAtIsNull(UUID id, UUID orgId);

    List<AppUser> findByOrgIdAndDeletedAtIsNullAndStatus(UUID orgId, AppUser.Status status);

    // M7: "builder contact" on a generated document -- the org owner's own
    // mobile stands in for a registered-office phone number, which this
    // codebase has no separate field for (see Organization entity).
    Optional<AppUser> findFirstByOrgIdAndOwnerTrueAndDeletedAtIsNull(UUID orgId);
}
