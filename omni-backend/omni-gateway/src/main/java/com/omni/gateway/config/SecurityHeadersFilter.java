package com.omni.gateway.config;

import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilter;
import org.springframework.web.server.WebFilterChain;
import reactor.core.publisher.Mono;

/**
 * 安全响应头过滤器（WebFlux 响应式技术栈）。
 * <p>
 * 为所有经过网关的响应添加安全相关的 HTTP 头，
 * 提供基础的浏览器端安全防护：
 * <ul>
 *   <li>{@code X-Content-Type-Options: nosniff} — 防止浏览器 MIME 类型嗅探</li>
 *   <li>{@code X-Frame-Options: SAMEORIGIN} — 防止点击劫持（仅允许同源 iframe）</li>
 *   <li>{@code Referrer-Policy: strict-origin-when-cross-origin} — 控制 Referer 头泄露</li>
 * </ul>
 * </p>
 */
@Component
public class SecurityHeadersFilter implements WebFilter {

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, WebFilterChain chain) {
        HttpHeaders headers = exchange.getResponse().getHeaders();
        headers.add("X-Content-Type-Options", "nosniff");
        headers.add("X-Frame-Options", "SAMEORIGIN");
        headers.add("Referrer-Policy", "strict-origin-when-cross-origin");
        return chain.filter(exchange);
    }
}
