package com.shardeya.platform;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;

import java.util.function.Supplier;

/**
 * B-04/B-05 API contract: sale creation and payment recording both take an
 * {@code Idempotency-Key} header so a retried request (network blip,
 * double-click, or "two staff recording the same payment simultaneously"
 * per B-05 §10) replays the original result instead of creating a second
 * sale/payment. Scoped per (org, key, endpoint) -- the same key reused for
 * a genuinely different endpoint is not a collision.
 *
 * <p>Callers with no {@code Idempotency-Key} header (key == null/blank) skip
 * tracking entirely and just run the action -- the header is optional, not
 * required, per the API spec's own bracket notation.
 */
@Service
public class IdempotencyService {

    private final IdempotencyKeyRepository repository;
    private final TenantContextBinder tenantContextBinder;
    private final ObjectMapper objectMapper;

    public IdempotencyService(IdempotencyKeyRepository repository, TenantContextBinder tenantContextBinder, ObjectMapper objectMapper) {
        this.repository = repository;
        this.tenantContextBinder = tenantContextBinder;
        this.objectMapper = objectMapper;
    }

    public <T> T withIdempotency(String endpoint, String idempotencyKey, Class<T> responseType, Supplier<T> action) {
        if (idempotencyKey == null || idempotencyKey.isBlank()) {
            return action.get();
        }
        var orgId = tenantContextBinder.currentOrgId();
        var id = new IdempotencyKeyEntity.Id(orgId, idempotencyKey, endpoint);

        return repository.findById(id)
                .map(existing -> readJson(existing.getResponseBody(), responseType))
                .orElseGet(() -> {
                    T result = action.get();
                    repository.save(new IdempotencyKeyEntity(orgId, idempotencyKey, endpoint, 200, writeJson(result)));
                    return result;
                });
    }

    private String writeJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (Exception e) {
            throw new IllegalStateException("Failed to serialize idempotent response", e);
        }
    }

    private <T> T readJson(String json, Class<T> type) {
        try {
            return objectMapper.readValue(json, type);
        } catch (Exception e) {
            throw new IllegalStateException("Failed to deserialize cached idempotent response", e);
        }
    }
}
