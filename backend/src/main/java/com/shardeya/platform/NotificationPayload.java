package com.shardeya.platform;

import java.util.Map;
import java.util.UUID;

/**
 * @param recipientUserId null broadcasts to every active user in the org
 *                        (NotificationService.createForOrg's original
 *                        contract); non-null targets exactly that one user
 *                        (M4: lead assignment, staff/team events) -- added
 *                        because M1-M3 never needed anything narrower than
 *                        "the whole org."
 */
public record NotificationPayload(UUID orgId, String typeCode, String titleKey, String bodyKey,
                                   Map<String, Object> params, String entityType, UUID entityId,
                                   UUID recipientUserId) {

    /** Convenience for the pre-M4 broadcast-to-org case. */
    public NotificationPayload(UUID orgId, String typeCode, String titleKey, String bodyKey,
                                Map<String, Object> params, String entityType, UUID entityId) {
        this(orgId, typeCode, titleKey, bodyKey, params, entityType, entityId, null);
    }
}
