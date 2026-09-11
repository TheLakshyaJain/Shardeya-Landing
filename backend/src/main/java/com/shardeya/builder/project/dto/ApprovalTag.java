package com.shardeya.builder.project.dto;

import java.util.UUID;

public record ApprovalTag(String label, String note, UUID docMediaId) {
}
