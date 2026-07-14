package com.company.gateway.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.reactive.EnableWebFluxSecurity;
import org.springframework.security.config.web.server.ServerHttpSecurity;
import org.springframework.security.web.server.SecurityWebFilterChain;

/**
 * Edge security for the gateway.
 *
 * <p>Validates the JWT (signature, issuer, expiry) as an OAuth2 resource server and rejects
 * invalid tokens with 401 before routing protected requests (FR-GW-03, NFR-SEC-01). Health and
 * metrics endpoints and public catalog reads are left unauthenticated; everything else requires a
 * valid token, with fine-grained RBAC enforced by the downstream services.
 */
@Configuration
@EnableWebFluxSecurity
public class SecurityConfig {

    @Bean
    SecurityWebFilterChain securityWebFilterChain(ServerHttpSecurity http) {
        http
            .csrf(ServerHttpSecurity.CsrfSpec::disable)
            .authorizeExchange(exchange -> exchange
                // Health/metrics reachable for probes; details are not exposed (NFR-OBS-08).
                .pathMatchers("/actuator/health/**", "/actuator/prometheus", "/actuator/info").permitAll()
                // Public, unauthenticated catalog reads for guests (A2, NFR-SEC-01).
                .pathMatchers(HttpMethod.GET, "/api/v1/products/**").permitAll()
                .anyExchange().authenticated())
            .oauth2ResourceServer(oauth2 -> oauth2.jwt(Customizer.withDefaults()));
        return http.build();
    }
}
