package com.shardeya.platform;

import java.util.Map;

/** 429 — a rate limit (OTP send, login attempts, etc.) was exceeded. */
public class TooManyRequestsException extends RuntimeException {

    private final String messageKey;
    private final Map<String, Object> params;

    public TooManyRequestsException(String messageKey, Map<String, Object> params) {
        super(messageKey);
        this.messageKey = messageKey;
        this.params = params;
    }

    public String messageKey() {
        return messageKey;
    }

    public Map<String, Object> params() {
        return params;
    }
}
