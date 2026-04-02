package com.hms.gateway.filter;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.util.UUID;

/**
 * Global Gateway filter that generates a correlationId for every request,
 * propagates it downstream as X-Correlation-Id, and stamps every forwarded
 * request with X-Internal-Secret so downstream services can reject calls
 * that did not originate from the gateway.
 */
@Slf4j
@Component
public class CorrelationIdFilter implements GlobalFilter, Ordered {

    private static final String CORRELATION_HEADER  = "X-Correlation-Id";
    private static final String INTERNAL_SECRET_HEADER = "X-Internal-Secret";

    @Value("${internal.secret}")
    private String internalSecret;

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        ServerHttpRequest request = exchange.getRequest();

        // Reuse existing correlation ID if present (e.g. from upstream caller)
        String correlationId = request.getHeaders().getFirst(CORRELATION_HEADER);
        if (correlationId == null || correlationId.isBlank()) {
            correlationId = UUID.randomUUID().toString().replace("-", "").substring(0, 8);
        }

        final String finalCorrelationId = correlationId;

        // Add correlation ID + internal secret to downstream request headers
        ServerHttpRequest mutatedRequest = request.mutate()
                .header(CORRELATION_HEADER, finalCorrelationId)
                .header(INTERNAL_SECRET_HEADER, internalSecret)
                .build();

        ServerHttpResponse response = exchange.getResponse();
        response.getHeaders().add(CORRELATION_HEADER, finalCorrelationId);

        String method = request.getMethod().name();
        String path   = request.getPath().value();

        log.debug("[{}] {} {} → routing to downstream", finalCorrelationId, method, path);

        return chain.filter(exchange.mutate().request(mutatedRequest).build())
                .doOnSuccess(v  -> log.debug("[{}] {} {} completed", finalCorrelationId, method, path))
                .doOnError(err  -> log.error("[{}] {} {} error: {}", finalCorrelationId, method, path, err.getMessage()));
    }

    @Override
    public int getOrder() {
        // Run before AuthenticationFilter (order -1 ensures it is first)
        return Ordered.HIGHEST_PRECEDENCE;
    }
}
