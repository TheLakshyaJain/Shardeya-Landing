package com.shardeya.foundation.auth.dto;

import java.util.List;

public record MeResponse(
        UserSummary user, OrgSummary org, List<String> permissions, EntitlementsSummary entitlements,
        int unreadCount) {
}
