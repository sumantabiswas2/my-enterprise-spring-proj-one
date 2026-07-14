package com.company.gateway.config;

import org.springframework.cloud.gateway.filter.ratelimit.KeyResolver;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.core.context.ReactiveSecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import reactor.core.publisher.Mono;

/**
 * Resolves the rate-limiting key per client (FR-GW-05).
 *
 * <p>Authenticated requests are limited per JWT subject; unauthenticated requests fall back to the
 * remote address so public catalog browsing is still bounded.
 */
@Configuration
public class RateLimiterConfig {

    @Bean
    KeyResolver userKeyResolver() {
        return exchange -> ReactiveSecurityContextHolder.getContext()
            .map(ctx -> ctx.getAuthentication())
            .filter(auth -> auth instanceof JwtAuthenticationToken)
            .map(auth -> ((Jwt) auth.getPrincipal()).getSubject())
            .switchIfEmpty(Mono.fromSupplier(() -> {
                var remote = exchange.getRequest().getRemoteAddress();
                return remote != null ? remote.getAddress().getHostAddress() : "anonymous";
            }));
    }
}
