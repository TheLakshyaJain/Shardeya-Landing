package com.shardeya.platform;

/** 401 — missing, invalid, or expired credentials/token. */
public class UnauthorizedException extends RuntimeException {

    private final String messageKey;

    public UnauthorizedException(String messageKey) {
        super(messageKey);
        this.messageKey = messageKey;
    }

    public String messageKey() {
        return messageKey;
    }
}
