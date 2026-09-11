package com.shardeya.platform;

import java.util.Map;

/**
 * A single field/rule-level error inside a RFC 9457 Problem Detail's {@code errors[]}
 * array (00-ARCHITECTURE.md §4.9). {@code messageKey} + {@code params} are resolved
 * client-side via i18next — the backend never renders a sentence (CLAUDE.md pitfall #14).
 */
public record ApiError(String field, String code, String messageKey, Map<String, Object> params) {
}
