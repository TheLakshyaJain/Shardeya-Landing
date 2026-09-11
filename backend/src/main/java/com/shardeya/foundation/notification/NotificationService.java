package com.shardeya.foundation.notification;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.shardeya.foundation.auth.AppUser;
import com.shardeya.foundation.auth.AppUserRepository;
import com.shardeya.foundation.notification.dto.NotificationResponse;
import com.shardeya.platform.ResourceNotFoundException;
import com.shardeya.platform.TenantContextBinder;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
public class NotificationService {

    private final NotificationRepository repository;
    private final AppUserRepository appUserRepository;
    private final TenantContextBinder tenantContextBinder;
    private final ObjectMapper objectMapper;

    public NotificationService(NotificationRepository repository, AppUserRepository appUserRepository,
                                TenantContextBinder tenantContextBinder, ObjectMapper objectMapper) {
        this.repository = repository;
        this.appUserRepository = appUserRepository;
        this.tenantContextBinder = tenantContextBinder;
        this.objectMapper = objectMapper;
    }

    /**
     * Fans out to every active user in the org -- in practice just the
     * single owner today (staff roles don't exist until M4), but this is
     * the one write path every M3 "in-app to owner + staff" notification
     * in B-04/B-05's spec goes through, so it's already correct once M4
     * adds real staff members.
     */
    public void createForOrg(UUID orgId, String typeCode, String titleKey, String bodyKey,
                              Map<String, Object> params, String entityType, UUID entityId) {
        String paramsJson = writeJson(params);
        for (AppUser user : appUserRepository.findByOrgIdAndDeletedAtIsNull(orgId)) {
            repository.save(new Notification(UUID.randomUUID(), orgId, user.getId(), typeCode, titleKey, bodyKey,
                    paramsJson, entityType, entityId));
        }
    }

    /** M4: a single targeted recipient (lead assignment, staff/team events) -- see NotificationPayload's own javadoc for why this exists alongside createForOrg. */
    public void createForUser(UUID orgId, UUID recipientUserId, String typeCode, String titleKey, String bodyKey,
                               Map<String, Object> params, String entityType, UUID entityId) {
        String paramsJson = writeJson(params);
        repository.save(new Notification(UUID.randomUUID(), orgId, recipientUserId, typeCode, titleKey, bodyKey,
                paramsJson, entityType, entityId));
    }

    public List<NotificationResponse> listMine(int limit) {
        UUID userId = tenantContextBinder.current().userId();
        return repository.findByRecipientUserIdOrderByCreatedAtDesc(userId, PageRequest.of(0, Math.min(Math.max(limit, 1), 100)))
                .stream().map(this::toResponse).toList();
    }

    public long unreadCount() {
        return repository.countByRecipientUserIdAndReadAtIsNull(tenantContextBinder.current().userId());
    }

    public void markRead(UUID notificationId) {
        Notification notification = repository.findById(notificationId)
                .orElseThrow(() -> new ResourceNotFoundException("error.notFound"));
        // Own-notification check IS the tenant/access guard here -- a
        // notification addressed to a different user in the same org is
        // just as inaccessible as one from a different org (404, not 403,
        // per CLAUDE.md rule #1's "foreign resource -> 404" logic applied
        // to "not yours" instead of "not your org").
        if (!notification.getRecipientUserId().equals(tenantContextBinder.current().userId())) {
            throw new ResourceNotFoundException("error.notFound");
        }
        if (notification.getReadAt() == null) {
            notification.setReadAt(java.time.Instant.now());
            repository.save(notification);
        }
    }

    // Spring Data's repository proxy defaults query methods to a read-only
    // transaction unless the calling code already has a writable one open --
    // a bare @Modifying UPDATE query with no surrounding @Transactional here
    // failed every time with "InvalidDataAccessApiUsageException: Executing
    // an update/delete query" (confirmed live: the "Mark all read" button
    // silently did nothing because the frontend's onError handler had
    // nowhere to surface it). RefreshTokenService.revokeFamilyOf() already
    // established the correct pattern for this exact class of bug --
    // @Transactional on the SERVICE method that calls a @Modifying query,
    // not on the repository method itself.
    @Transactional
    public void markAllRead() {
        repository.markAllRead(tenantContextBinder.current().userId());
    }

    private String writeJson(Map<String, Object> params) {
        try {
            return objectMapper.writeValueAsString(params == null ? Map.of() : params);
        } catch (Exception e) {
            throw new IllegalStateException("Failed to serialize notification params", e);
        }
    }

    private NotificationResponse toResponse(Notification n) {
        Map<String, Object> paramsMap;
        try {
            paramsMap = objectMapper.readValue(n.getParams(), Map.class);
        } catch (Exception e) {
            paramsMap = Map.of();
        }
        return new NotificationResponse(n.getId(), n.getTypeCode(), n.getTitleKey(), n.getBodyKey(), paramsMap,
                n.getEntityType(), n.getEntityId(), n.getReadAt() != null, n.getCreatedAt());
    }
}
