package com.shardeya.platform;

import org.springframework.stereotype.Service;

import java.util.UUID;

@Service
public class SensitiveAccessLogService {

    private final SensitiveAccessLogRepository repository;
    private final TenantContextBinder tenantContextBinder;

    public SensitiveAccessLogService(SensitiveAccessLogRepository repository, TenantContextBinder tenantContextBinder) {
        this.repository = repository;
        this.tenantContextBinder = tenantContextBinder;
    }

    public void record(String entityType, UUID entityId, String field, String reason) {
        repository.save(new SensitiveAccessLog(UUID.randomUUID(), tenantContextBinder.currentOrgId(),
                tenantContextBinder.current().userId(), entityType, entityId, field, reason));
    }
}
