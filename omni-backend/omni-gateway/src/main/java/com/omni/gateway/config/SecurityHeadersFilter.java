package com.omni.gateway.config;

import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilter;
import org.springframework.web.server.WebFilterChain;
import reactor.core.publisher.Mono;

import java.util.UUID;

/**
 * 安全响应头过滤器（WebFlux 响应式技术栈）。
 * <p>
 * 为所有经过网关的响应添加安全相关的 HTTP 头，
 * 提供基础的浏览器端安全防护：
 * <ul>
 *   <li>{@code X-Content-Type-Options: nosniff} — 防止浏览器 MIME 类型嗅探</li>
 *   <li>{@code X-Frame-Options: DENY} — 禁止页面被 iframe 嵌套</li>
 *   <li>{@code Referrer-Policy: strict-origin-when-cross-origin} — 控制 Referer 头泄露</li>
 * </ul>
 * </p>
 *
 * @see com.omni.gateway.filter.AuthFilter
 */
@Component
public class SecurityHeadersFilter implements WebFilter {

    /**
     * 为响应添加安全 HTTP 头。
     * <p>
     * 添加以下安全头：</p>
     * <ul>
     *   <li>{@code X-Content-Type-Options: nosniff} — 禁止浏览器 MIME 类型喗探，防止 XSS 等攻击</li>
     *   <li>{@code X-Frame-Options: DENY} — 禁止 iframe 嵌套，防止点击劫持</li>
     *   <li>{@code Referrer-Policy: strict-origin-when-cross-origin} — 跨域请求仅发送 origin，防止 Referer 泄露敏感信息</li>
     * </ul>
     *
     * @param exchange 当前请求/响应上下文
     * @param chain    过滤器链
     * @return 过滤器链继续执行的信号
     */
    @Override
    public Mono<Void> filter(ServerWebExchange exchange, WebFilterChain chain) {
        String traceId = UUID.randomUUID().toString().replace("-", "");
        ServerWebExchange tracedExchange = exchange.mutate()
                .request(exchange.getRequest().mutate()
                        .headers(headers -> headers.set("X-Trace-Id", traceId))
                        .build())
                .build();
        exchange.getResponse().beforeCommit(() -> {
            HttpHeaders headers = exchange.getResponse().getHeaders();
            headers.set("X-Trace-Id", traceId);
            headers.set("X-Content-Type-Options", "nosniff");
            headers.set("X-Frame-Options", "DENY");
            headers.set("Referrer-Policy", "strict-origin-when-cross-origin");
            return Mono.empty();
        });
        return chain.filter(tracedExchange);
    }
}
