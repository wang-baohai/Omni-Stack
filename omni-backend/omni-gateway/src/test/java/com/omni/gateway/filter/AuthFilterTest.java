package com.omni.gateway.filter;

import com.omni.gateway.config.JwkKeyProvider;
import org.junit.jupiter.api.Test;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.data.redis.core.ReactiveStringRedisTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

/** 网关认证过滤器边界测试。 */
class AuthFilterTest {

    /** 公开路径也必须清除客户端伪造的身份头和内部令牌。 */
    @Test
    void shouldStripSpoofedIdentityHeadersOnPublicPath() {
        AuthFilter filter = filter();
        MockServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.get("/api/auth/captcha")
                        .header("Authorization", "Bearer forged")
                        .header("X-User-Id", "999")
                        .header("X-Tenant-Id", "999")
                        .header("X-Internal-Token", "forged")
                        .header("X-Gateway-Forwarded", "false"));
        AtomicReference<ServerWebExchange> forwarded = new AtomicReference<>();
        GatewayFilterChain chain = current -> {
            forwarded.set(current);
            return Mono.empty();
        };

        filter.filter(exchange, chain).block();

        assertThat(forwarded.get()).isNotNull();
        assertThat(forwarded.get().getRequest().getHeaders().get("Authorization")).isNull();
        assertThat(forwarded.get().getRequest().getHeaders().get("X-User-Id")).isNull();
        assertThat(forwarded.get().getRequest().getHeaders().get("X-Tenant-Id")).isNull();
        assertThat(forwarded.get().getRequest().getHeaders().get("X-Internal-Token")).isNull();
        assertThat(forwarded.get().getRequest().getHeaders().getFirst("X-Gateway-Forwarded"))
                .isEqualTo("true");
    }

    /** 受保护路径缺少 Bearer Token 时必须在网关返回 401。 */
    @Test
    void shouldRejectProtectedPathWithoutBearerToken() {
        AuthFilter filter = filter();
        MockServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.get("/api/crm/lead/list"));

        filter.filter(exchange, current -> Mono.error(new AssertionError("不应转发请求"))).block();

        assertThat(exchange.getResponse().getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    private AuthFilter filter() {
        return new AuthFilter(mock(JwkKeyProvider.class), mock(ReactiveStringRedisTemplate.class));
    }
}
