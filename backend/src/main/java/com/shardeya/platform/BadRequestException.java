package com.shardeya.platform;

import java.util.List;
import java.util.Map;

/** 400 — request is well-formed but fails a business validation rule. */
public class BadRequestException extends RuntimeException {

    private final List<ApiError> errors;

    public BadRequestException(String field, String code, String messageKey) {
        this(List.of(new ApiError(field, code, messageKey, Map.of())));
    }

    public BadRequestException(List<ApiError> errors) {
        super(errors.isEmpty() ? "Bad request" : errors.get(0).messageKey());
        this.errors = errors;
    }

    public List<ApiError> errors() {
        return errors;
    }
}
