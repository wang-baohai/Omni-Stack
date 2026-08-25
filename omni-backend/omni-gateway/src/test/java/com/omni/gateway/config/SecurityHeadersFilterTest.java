package com.omni.gateway.config;

import io.micrometer.tracing.Span;
import io.micrometer.tracing.TraceContext;
import io.micrometer.tracing.Tracer;
import io.micrometer.tracing.handler.TracingObservationHandler;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.http.HttpHeaders;
import org.springframework.http.server.reactive.observation.ServerRequestObservationContext;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.springframework.web.server.WebFilterChain;
import reactor.core.publisher.Mono;

import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

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

    /** 开启追踪后必须覆盖伪造兼容头，并把真实 traceId 同时传给下游和客户端。 */
    @Test
    void shouldMapCurrentSpanToCompatibilityHeader() {
        Tracer tracer = mock(Tracer.class);
        Span span = mock(Span.class);
        TraceContext context = mock(TraceContext.class);
        @SuppressWarnings("unchecked")
        ObjectProvider<Tracer> provider = mock(ObjectProvider.class);
        when(provider.getIfAvailable()).thenReturn(tracer);
        when(tracer.currentSpan()).thenReturn(span);
        when(span.context()).thenReturn(context);
        when(context.traceId()).thenReturn("abcdef0123456789abcdef0123456789");
        SecurityHeadersFilter filter = new SecurityHeadersFilter(provider);
        MockServerWebExchange exchange = MockServerWebExchange.from(MockServerHttpRequest.get("/api/test")
                .header("X-Trace-Id", "11111111111111111111111111111111"));
        AtomicReference<String> downstreamTraceId = new AtomicReference<>();
        WebFilterChain chain = current -> {
            downstreamTraceId.set(current.getRequest().getHeaders().getFirst("X-Trace-Id"));
            return current.getResponse().setComplete();
        };

        filter.filter(exchange, chain).block();

        assertThat(downstreamTraceId.get()).isEqualTo("abcdef0123456789abcdef0123456789");
        assertThat(exchange.getResponse().getHeaders().getFirst("X-Trace-Id"))
                .isEqualTo("abcdef0123456789abcdef0123456789");
    }

    /** Reactor 线程没有 ThreadLocal span 时必须从服务端 Observation 上下文读取 traceId。 */
    @Test
    void shouldReadTraceIdFromServerObservationContext() {
        Span span = mock(Span.class);
        TraceContext traceContext = mock(TraceContext.class);
        when(span.context()).thenReturn(traceContext);
        when(traceContext.traceId()).thenReturn("1234567890abcdef1234567890abcdef");
        SecurityHeadersFilter filter = new SecurityHeadersFilter();
        MockServerWebExchange exchange = MockServerWebExchange.from(MockServerHttpRequest.get("/api/test"));
        ServerRequestObservationContext observationContext = new ServerRequestObservationContext(
                exchange.getRequest(), exchange.getResponse(), exchange.getAttributes());
        TracingObservationHandler.TracingContext tracingContext =
                new TracingObservationHandler.TracingContext();
        tracingContext.setSpan(span);
        observationContext.put(TracingObservationHandler.TracingContext.class, tracingContext);
        exchange.getAttributes().put(
                ServerRequestObservationContext.CURRENT_OBSERVATION_CONTEXT_ATTRIBUTE, observationContext);

        filter.filter(exchange, current -> current.getResponse().setComplete()).block();

        assertThat(exchange.getResponse().getHeaders().getFirst("X-Trace-Id"))
                .isEqualTo("1234567890abcdef1234567890abcdef");
    }
}
