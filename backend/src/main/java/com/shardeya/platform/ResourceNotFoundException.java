package com.shardeya.platform;

/**
 * Thrown for both "row doesn't exist" and "row belongs to a foreign org" — the two
 * cases are indistinguishable to the client by design (CLAUDE.md rule #1: foreign-org
 * resources return 404, never 403).
 */
public class ResourceNotFoundException extends RuntimeException {

    private final String messageKey;

    public ResourceNotFoundException(String messageKey) {
        super(messageKey);
        this.messageKey = messageKey;
    }

    public String messageKey() {
        return messageKey;
    }
}
