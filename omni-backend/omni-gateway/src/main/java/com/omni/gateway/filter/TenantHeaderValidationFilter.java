package com.omni.gateway.filter;

import lombok.extern.slf4j.Slf4j;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.nio.charset.StandardCharsets;
import java.util.List;

/**
 * Gateway 租户标识校验过滤器。
 * <p>
 * 拦截所有 {@code /api/**} 请求，校验 {@code X-Tenant-Id} 请求头是否缺失。
 * 公开认证路径（登录、注册、验证码等）不受此校验约束。
 * </p>
 * <p>
 * 该过滤器在 {@link AuthFilter}（order=-100）之后执行（order=100），
 * AuthFilter 会从 JWT claims 中提取 tenant_id 注入到 {@code X-Tenant-Id} 头中，
 * 因此正常经过认证的请求都能通过校验。此过滤器主要起兜底作用：
 * <ul>
 *   <li>JWT 中缺少 tenant_id claim 的异常情况</li>
 *   <li>未来新增的不经过 AuthFilter 的路由</li>
 * </ul>
 * </p>
 *
 * @see AuthFilter
 */
@Slf4j
@Component
public class TenantHeaderValidationFilter implements GlobalFilter, Ordered {

    /** 租户标识请求头名称 */
    private static final String TENANT_HEADER = "X-Tenant-Id";

    /** 不需要租户标识的公开路径前缀 */
    private static final List<String> EXCLUDED_PREFIXES = List.of(
            "/api/auth/oauth2/",
            "/actuator/",
            "/oauth2/",
            "/.well-known/",
            "/login",
            "/error"
    );

    /** 不需要租户标识的精确路径 */
    private static final List<String> EXCLUDED_EXACT_PATHS = List.of(
            "/api/auth/login",
            "/api/auth/session-login",
            "/api/auth/register",
            "/api/auth/captcha",
            "/api/auth/tenants",
            "/favicon.ico"
    );

    /**
     * 过滤器主方法。
     * <p>
     * 对 {@code /api/**} 路径（排除公开认证路径）校验 {@code X-Tenant-Id} 请求头是否存在。
     * 缺失时返回 400 Bad Request，响应体为标准 JSON 格式。
     * </p>
     *
     * @param exchange 当前请求上下文
     * @param chain    过滤器链
     * @return 请求处理完成的信号
     */
    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        ServerHttpRequest request = exchange.getRequest();
        String path = request.getURI().getPath();

        // 非 /api/ 路径不校验
        if (!path.startsWith("/api/")) {
            return chain.filter(exchange);
        }

        // 公开路径不校验
        if (isExcluded(path)) {
            return chain.filter(exchange);
        }

        // /api/auth/ 前缀的路径不校验租户头（认证模块自行处理）
        if (path.startsWith("/api/auth/")) {
            return chain.filter(exchange);
        }

        // 校验 X-Tenant-Id 是否存在
        String tenantId = request.getHeaders().getFirst(TENANT_HEADER);
        if (tenantId == null || tenantId.isBlank()) {
            log.warn("缺少租户标识 X-Tenant-Id, path: {}", path);
            return writeBadRequestResponse(exchange, "缺少租户标识 X-Tenant-Id");
        }

        return chain.filter(exchange);
    }

    /**
     * 过滤器执行优先级。
     * <p>返回 100，确保在 {@link AuthFilter}（order=-100）之后执行。</p>
     *
     * @return 优先级值
     */
    @Override
    public int getOrder() {
        return 100;
    }

    /**
     * 判断路径是否为排除路径。
     *
     * @param path 请求路径
     * @return true 表示不需要校验租户头
     */
    private boolean isExcluded(String path) {
        for (String exact : EXCLUDED_EXACT_PATHS) {
            if (path.equals(exact)) {
                return true;
            }
        }
        for (String prefix : EXCLUDED_PREFIXES) {
            if (path.startsWith(prefix)) {
                return true;
            }
        }
        return false;
    }

    /**
     * 写入 400 Bad Request JSON 响应。
     *
     * @param exchange 当前请求上下文
     * @param message  错误消息
     * @return 响应写入完成的 Mono 信号
     */
    private Mono<Void> writeBadRequestResponse(ServerWebExchange exchange, String message) {
        exchange.getResponse().setStatusCode(HttpStatus.BAD_REQUEST);
        exchange.getResponse().getHeaders().setContentType(MediaType.APPLICATION_JSON);
        String json = "{\"code\":400,\"message\":\"" + message + "\",\"data\":null}";
        DataBuffer buffer = exchange.getResponse().bufferFactory()
                .wrap(json.getBytes(StandardCharsets.UTF_8));
        return exchange.getResponse().writeWith(Mono.just(buffer));
    }
}
