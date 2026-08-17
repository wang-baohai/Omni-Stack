package com.omni.gateway.filter;

import org.junit.jupiter.api.Test;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.http.HttpStatus;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import reactor.core.publisher.Mono;

import java.util.concurrent.atomic.AtomicBoolean;

import static org.assertj.core.api.Assertions.assertThat;

/** 网关租户头校验过滤器测试。 */
class TenantHeaderValidationFilterTest {

    /** 业务 API 缺少网关注入的租户头时必须失败关闭。 */
    @Test
    void shouldRejectProtectedApiWithoutTenantHeader() {
        TenantHeaderValidationFilter filter = new TenantHeaderValidationFilter();
        MockServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.get("/api/asset/list"));

        filter.filter(exchange, current -> Mono.error(new AssertionError("不应转发请求"))).block();

        assertThat(exchange.getResponse().getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    /** 已注入租户头的业务请求应继续进入过滤器链。 */
    @Test
    void shouldAllowApiWithTenantHeader() {
        TenantHeaderValidationFilter filter = new TenantHeaderValidationFilter();
        MockServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.get("/api/asset/list").header("X-Tenant-Id", "1"));
        AtomicBoolean forwarded = new AtomicBoolean();
        GatewayFilterChain chain = current -> {
            forwarded.set(true);
            return Mono.empty();
        };

        filter.filter(exchange, chain).block();

        assertThat(forwarded).isTrue();
    }
}
