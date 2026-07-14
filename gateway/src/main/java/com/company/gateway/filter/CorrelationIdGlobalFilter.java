package com.company.gateway.filter;

import java.util.UUID;

import org.slf4j.MDC;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;

import reactor.core.publisher.Mono;

/**
 * Originates or propagates {@code X-Correlation-Id} on every request and exposes it to downstream
 * logs (FR-GW-04). The correlation ID is a business identifier distinct from the W3C trace ID; both
 * must be available downstream. W3C trace context propagation is handled separately by Micrometer
 * Tracing (NFR-OBS-06); this filter only manages the correlation ID.
 */
@Component
public class CorrelationIdGlobalFilter implements GlobalFilter, Ordered {

    public static final String CORRELATION_ID_HEADER = "X-Correlation-Id";
    private static final String MDC_KEY = "correlationId";

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        String correlationId = exchange.getRequest().getHeaders().getFirst(CORRELATION_ID_HEADER);
        if (correlationId == null || correlationId.isBlank()) {
            correlationId = UUID.randomUUID().toString();
        }
        final String cid = correlationId;

        // Forward the correlation ID downstream and echo it back to the caller.
        ServerHttpRequest mutated = exchange.getRequest().mutate()
            .header(CORRELATION_ID_HEADER, cid)
            .build();
        exchange.getResponse().getHeaders().set(CORRELATION_ID_HEADER, cid);

        return chain.filter(exchange.mutate().request(mutated).build())
            .contextWrite(ctx -> ctx.put(MDC_KEY, cid))
            .doOnEach(signal -> {
                try (MDC.MDCCloseable ignored = MDC.putCloseable(MDC_KEY, cid)) {
                    // MDC is populated for any logging that happens on this signal.
                }
            });
    }

    @Override
    public int getOrder() {
        // Run first so the correlation ID is present for all later filters.
        return Ordered.HIGHEST_PRECEDENCE;
    }
}
