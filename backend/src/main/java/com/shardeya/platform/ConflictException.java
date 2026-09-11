package com.shardeya.platform;

import java.util.Map;

/** 409 — the request conflicts with existing state (e.g. mobile already registered). */
public class ConflictException extends RuntimeException {

    private final String messageKey;
    private final Map<String, Object> params;

    public ConflictException(String messageKey) {
        this(messageKey, Map.of());
    }

    /** M4: duplicate-customer detection needs to hand the frontend the existing record's name/status/id (M-12 §7 "Open existing / Create anyway"), same params-carrying shape QuotaExceededException already established. */
    public ConflictException(String messageKey, Map<String, Object> params) {
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
