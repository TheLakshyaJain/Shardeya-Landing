package com.shardeya.foundation.auth.dto;

import java.util.UUID;

public record OrgSummary(UUID id, String type, String name, String city) {
}
