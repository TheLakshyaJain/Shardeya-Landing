package com.shardeya.platform;

/**
 * 403 — a boolean/tier plan gate (e.g. {@code BULK_UPLOAD_ENABLED}), as
 * opposed to {@link QuotaExceededException}'s numeric used/limit shape.
 * There is no "used" or "limit" to report, just "this plan doesn't include
 * this feature at all."
 */
public class FeatureNotEnabledException extends RuntimeException {

    private final String featureKey;

    public FeatureNotEnabledException(String messageKey, String featureKey) {
        super(messageKey);
        this.featureKey = featureKey;
    }

    public String messageKey() {
        return getMessage();
    }

    public String featureKey() {
        return featureKey;
    }
}
