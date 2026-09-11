package com.shardeya.platform;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface SensitiveAccessLogRepository extends JpaRepository<SensitiveAccessLog, UUID> {
}
