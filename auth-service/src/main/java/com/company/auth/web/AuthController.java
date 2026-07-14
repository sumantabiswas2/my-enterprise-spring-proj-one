package com.company.auth.web;

import java.util.List;
import java.util.Map;

import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.security.core.Authentication;

/**
 * Thin identity facade over Keycloak. Keycloak owns registration, credentials, and tokens
 * (FR-AUTH-01, FR-AUTH-06); this service simply exposes the authenticated principal derived from a
 * validated JWT, which is useful for demos and for confirming the M0 auth path end to end.
 */
@RestController
@RequestMapping("/api/v1/auth")
@Tag(name = "Auth", description = "Identity facade over Keycloak")
public class AuthController {

    @GetMapping("/me")
    @Operation(summary = "Return the authenticated subject and roles from the validated JWT")
    public Map<String, Object> me(@AuthenticationPrincipal Jwt jwt, Authentication authentication) {
        List<String> roles = authentication.getAuthorities().stream()
            .map(GrantedAuthority::getAuthority)
            .toList();
        return Map.of(
            "subject", jwt.getSubject(),
            "issuer", String.valueOf(jwt.getIssuer()),
            "roles", roles,
            "expiresAt", String.valueOf(jwt.getExpiresAt()));
    }
}
