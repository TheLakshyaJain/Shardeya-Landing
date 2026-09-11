package com.shardeya.builder.team.dto;

import java.util.UUID;

/** Exactly one of reassignToUserId / leaveUnassigned must be meaningful when the target has assigned leads (B-12 §7 "reassignment prompt"). */
public record DeactivateRequest(String reason, UUID reassignToUserId, boolean leaveUnassigned) {
}
