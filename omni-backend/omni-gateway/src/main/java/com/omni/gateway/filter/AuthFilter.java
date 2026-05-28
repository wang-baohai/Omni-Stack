package com.omni.gateway.filter;

import lombok.extern.slf4j.Slf4j;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

/**
 * Global authentication filter.
 * Add token validation logic here.
 */
@Slf4j
@Component
public class AuthFilter implements GlobalFilter, Ordered {

    private static final String AUTH_HEADER = "Authorization";

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        // Stub: currently passes all requests through.
        // In production, missing/invalid tokens should return HTTP 401.
        ServerHttpRequest request = exchange.getRequest();
        String path = request.getURI().getPath();

        // Skip authentication for public paths
        if (isPublicPath(path)) {
            return chain.filter(exchange);
        }

        // Check for authorization token
        String token = request.getHeaders().getFirst(AUTH_HEADER);
        if (token == null || token.isBlank()) {
            log.warn("Missing authorization token for path: {}", path);
            // TODO: [gateway] Return 401 response or implement token validation
        }

        // TODO: [gateway] Validate token and extract user info
        // Add user info to request headers for downstream services
        return chain.filter(exchange);
    }

    @Override
    public int getOrder() {
        return -100;
    }

    private boolean isPublicPath(String path) {
        return path.startsWith("/api/auth/") ||
               path.startsWith("/actuator/") ||
               path.equals("/favicon.ico");
    }
}
