package com.company.customer.adapter.in.rest.dto;

import java.time.Instant;

/**
 * Standard single-resource success envelope (docs/api-guidelines.md → Responses).
 */
public record ApiResponse<T>(T data, Instant timestamp, String traceId) {

    public static <T> ApiResponse<T> of(T data, String traceId) {
        return new ApiResponse<>(data, Instant.now(), traceId);
    }
}
