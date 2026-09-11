package com.shardeya.platform;

/**
 * 403 — M-09's "defence in depth" server-side quota rejection
 * (02-FOUNDATION-MODULES.md M-09 §7: "the create endpoint also rejects with
 * code=QUOTA_EXCEEDED and a payload containing {limitKey, used, limit,
 * upgradeTo}"). The client should have already disabled the triggering
 * button pre-flight — this is the backstop, not the primary UX.
 */
public class QuotaExceededException extends RuntimeException {

    private final String limitKey;
    private final int used;
    private final int limit;

    public QuotaExceededException(String messageKey, String limitKey, int used, int limit) {
        super(messageKey);
        this.limitKey = limitKey;
        this.used = used;
        this.limit = limit;
    }

    public String messageKey() {
        return getMessage();
    }

    public String limitKey() {
        return limitKey;
    }

    public int used() {
        return used;
    }

    public int limit() {
        return limit;
    }
}
