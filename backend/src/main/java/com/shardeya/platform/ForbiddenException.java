package com.shardeya.platform;

/**
 * 403 — authenticated, but genuinely lacks the capability (not a foreign-tenant
 * lookup, which is always {@link ResourceNotFoundException}/404 instead — see
 * CLAUDE.md rule #1).
 */
public class ForbiddenException extends RuntimeException {

    private final String messageKey;

    public ForbiddenException(String messageKey) {
        super(messageKey);
        this.messageKey = messageKey;
    }

    public String messageKey() {
        return messageKey;
    }
}
