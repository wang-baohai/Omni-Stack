package com.omni.gateway.config;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.springframework.web.server.WebFilterChain;
import reactor.core.publisher.Mono;

import static org.assertj.core.api.Assertions.assertThat;

/** 网关安全响应头过滤器测试。 */
class SecurityHeadersFilterTest {

    /** 下游即使追加同名响应头，提交前也必须收敛为唯一安全值。 */
    @Test
    void shouldSetUniqueSecurityHeadersBeforeCommit() {
        SecurityHeadersFilter filter = new SecurityHeadersFilter();
        MockServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.get("/api/auth/captcha"));
        WebFilterChain chain = current -> {
            current.getResponse().getHeaders().add("X-Content-Type-Options", "duplicate");
            current.getResponse().getHeaders().add("X-Frame-Options", "SAMEORIGIN");
            return current.getResponse().setComplete();
        };

        filter.filter(exchange, chain).block();

        HttpHeaders headers = exchange.getResponse().getHeaders();
        assertThat(headers.get("X-Content-Type-Options")).containsExactly("nosniff");
        assertThat(headers.get("X-Frame-Options")).containsExactly("DENY");
        assertThat(headers.get("Referrer-Policy"))
                .containsExactly("strict-origin-when-cross-origin");
        assertThat(headers.getFirst("X-Trace-Id")).matches("[a-f0-9]{32}");
    }
}
