package com.company.customer.exception;

import java.time.Instant;
import java.util.List;

/**
 * Standard error envelope (docs/api-guidelines.md → Errors). Never carries a stack trace
 * or internal exception detail (NFR-SEC-05, AGENTS.md → Exception Handling).
 */
public record ErrorResponse(
        String code,
        String message,
        Instant timestamp,
        String traceId,
        List<FieldError> fieldErrors) {

    public record FieldError(String field, String message) {
    }

    public static ErrorResponse of(String code, String message, String traceId) {
        return new ErrorResponse(code, message, Instant.now(), traceId, List.of());
    }

    public static ErrorResponse of(String code, String message, String traceId, List<FieldError> fieldErrors) {
        return new ErrorResponse(code, message, Instant.now(), traceId, fieldErrors);
    }
}
