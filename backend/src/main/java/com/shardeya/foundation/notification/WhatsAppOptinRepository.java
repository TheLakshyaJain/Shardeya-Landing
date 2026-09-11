package com.shardeya.foundation.notification;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface WhatsAppOptinRepository extends JpaRepository<WhatsAppOptin, UUID> {

    Optional<WhatsAppOptin> findByOrgIdAndMobile(UUID orgId, String mobile);
}
