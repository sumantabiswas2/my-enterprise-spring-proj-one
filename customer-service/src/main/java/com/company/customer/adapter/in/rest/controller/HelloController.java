package com.company.customer.adapter.in.rest.controller;

import java.util.Map;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.company.customer.adapter.in.rest.dto.ApiResponse;

import io.micrometer.tracing.Tracer;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

/**
 * M0 milestone endpoint: proves the end-to-end path Gateway → JWT validation → routed service with
 * RBAC. This is temporary scaffolding to be replaced by the real Customer API in M1 (FR-CUST-*).
 */
@RestController
@RequestMapping("/api/v1/customers")
@Tag(name = "Customer", description = "Customer profile API (M0 hello scaffolding)")
public class HelloController {

    private final Tracer tracer;

    public HelloController(Tracer tracer) {
        this.tracer = tracer;
    }

    @GetMapping("/hello")
    @PreAuthorize("hasRole('CUSTOMER')")
    @Operation(summary = "Authenticated greeting demonstrating the M0 gateway → service → RBAC path")
    public ApiResponse<Map<String, String>> hello(@AuthenticationPrincipal Jwt jwt) {
        var data = Map.of(
            "message", "Hello from customer-service",
            "subject", jwt.getSubject());
        return ApiResponse.of(data, currentTraceId());
    }

    private String currentTraceId() {
        var span = tracer.currentSpan();
        return span != null ? span.context().traceId() : null;
    }
}
