package com.shardeya.foundation.rbac;

import java.util.List;
import java.util.UUID;

public record PermissionsMeResponse(List<String> permissions, List<UUID> projectScope, String scopeMode) {
}
