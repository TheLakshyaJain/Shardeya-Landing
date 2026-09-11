package com.shardeya.foundation.admin;

import com.shardeya.foundation.admin.dto.AppErrorLogRow;
import com.shardeya.foundation.admin.dto.MessageDeliveryLogRow;
import com.shardeya.foundation.notification.MessageDelivery;
import com.shardeya.foundation.notification.MessageDeliveryRepository;
import com.shardeya.platform.TenantContextBinder;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

/**
 * Backs the deliberately scoped-down M-14 stand-in described in CLAUDE.md's
 * "Post-M7 -- Minimal Ops Visibility" notes: just enough to answer "would we
 * notice before the customer tells us" -- a delivery log and a recent-errors
 * list, gated on the existing {@code SETTINGS_MANAGE} permission (Admin/Owner
 * only, already seeded since M1, never actually consulted anywhere until
 * now -- the same "seeded early, wired up later" pattern this project has
 * hit repeatedly). No separate platform-admin realm, no break-glass, no
 * cross-org visibility beyond the same nullable-org_id shape {@code role}
 * already uses for system-wide rows.
 */
@Service
public class AdminOpsService {

    private static final int MAX_LIMIT = 200;

    private final MessageDeliveryRepository messageDeliveryRepository;
    private final AppErrorLogRepository errorLogRepository;
    private final TenantContextBinder tenantContextBinder;

    public AdminOpsService(MessageDeliveryRepository messageDeliveryRepository, AppErrorLogRepository errorLogRepository,
                            TenantContextBinder tenantContextBinder) {
        this.messageDeliveryRepository = messageDeliveryRepository;
        this.errorLogRepository = errorLogRepository;
        this.tenantContextBinder = tenantContextBinder;
    }

    @Transactional(readOnly = true)
    public java.util.List<MessageDeliveryLogRow> messageDeliveries(MessageDelivery.Status status, int limit) {
        UUID orgId = tenantContextBinder.currentOrgId();
        var page = PageRequest.of(0, Math.clamp(limit, 1, MAX_LIMIT));
        var rows = status != null
                ? messageDeliveryRepository.findByOrgIdAndStatusOrderByCreatedAtDesc(orgId, status, page)
                : messageDeliveryRepository.findByOrgIdOrderByCreatedAtDesc(orgId, page);
        return rows.stream()
                .map(m -> new MessageDeliveryLogRow(m.getId(), m.getChannel(), m.getRecipientMasked(), m.getTemplateCode(),
                        m.getProvider(), m.getStatus(), m.getErrorCode(), m.getSentAt(), m.getCreatedAt()))
                .toList();
    }

    @Transactional(readOnly = true)
    public java.util.List<AppErrorLogRow> recentErrors(int limit) {
        UUID orgId = tenantContextBinder.currentOrgId();
        var page = PageRequest.of(0, Math.clamp(limit, 1, MAX_LIMIT));
        return errorLogRepository.findVisibleToOrg(orgId, page).stream()
                .map(e -> new AppErrorLogRow(e.getId(), e.getOccurredAt(), e.getHttpMethod(), e.getPath(),
                        e.getExceptionClass(), e.getMessage(), e.getOrgId() == null))
                .toList();
    }
}
