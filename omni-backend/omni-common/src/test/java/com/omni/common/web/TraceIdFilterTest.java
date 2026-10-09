package com.omni.common.web;

import io.micrometer.tracing.Span;
import io.micrometer.tracing.TraceContext;
import io.micrometer.tracing.Tracer;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/** Servlet 请求关联 ID 过滤器测试。 */
class TraceIdFilterTest {

    /** 合法网关追踪号应贯穿请求处理并在完成后清理 MDC。 */
    @Test
    void shouldPropagateSafeTraceIdAndClearMdc() throws Exception {
        TraceIdFilter filter = new TraceIdFilter();
        MockHttpServletRequest request = new MockHttpServletRequest();
        MockHttpServletResponse response = new MockHttpServletResponse();
        request.addHeader(TraceIdFilter.TRACE_HEADER, "0123456789abcdef0123456789abcdef");
        AtomicReference<String> traceInChain = new AtomicReference<>();

        filter.doFilter(request, response,
                (ignoredRequest, ignoredResponse) -> traceInChain.set(MDC.get(TraceIdFilter.MDC_KEY)));

        assertThat(traceInChain.get()).isEqualTo("0123456789abcdef0123456789abcdef");
        assertThat(response.getHeader(TraceIdFilter.TRACE_HEADER))
                .isEqualTo("0123456789abcdef0123456789abcdef");
        assertThat(MDC.get(TraceIdFilter.MDC_KEY)).isNull();
    }

    /** 非法客户端追踪号不得进入日志上下文。 */
    @Test
    void shouldReplaceUnsafeTraceId() throws Exception {
        TraceIdFilter filter = new TraceIdFilter();
        MockHttpServletRequest request = new MockHttpServletRequest();
        MockHttpServletResponse response = new MockHttpServletResponse();
        request.addHeader(TraceIdFilter.TRACE_HEADER, "bad trace\r\nspoofed");
        AtomicReference<String> traceInChain = new AtomicReference<>();

        filter.doFilter(request, response,
                (ignoredRequest, ignoredResponse) -> traceInChain.set(MDC.get(TraceIdFilter.MDC_KEY)));

        assertThat(traceInChain.get()).matches("[0-9a-f]{32}");
        assertThat(response.getHeader(TraceIdFilter.TRACE_HEADER)).isEqualTo(traceInChain.get());
        assertThat(MDC.get(TraceIdFilter.MDC_KEY)).isNull();
    }

    /** 开启追踪后兼容头必须映射到当前 W3C 上下文的真实 traceId。 */
    @Test
    void shouldPreferCurrentSpanTraceId() throws Exception {
        Tracer tracer = mock(Tracer.class);
        Span span = mock(Span.class);
        TraceContext context = mock(TraceContext.class);
        when(tracer.currentSpan()).thenReturn(span);
        when(span.context()).thenReturn(context);
        when(context.traceId()).thenReturn("abcdef0123456789abcdef0123456789");
        TraceIdFilter filter = new TraceIdFilter(tracer);
        MockHttpServletRequest request = new MockHttpServletRequest();
        MockHttpServletResponse response = new MockHttpServletResponse();
        request.addHeader(TraceIdFilter.TRACE_HEADER, "11111111111111111111111111111111");
        AtomicReference<String> traceInChain = new AtomicReference<>();

        filter.doFilter(request, response,
                (ignoredRequest, ignoredResponse) -> traceInChain.set(MDC.get(TraceIdFilter.MDC_KEY)));

        assertThat(traceInChain.get()).isEqualTo("abcdef0123456789abcdef0123456789");
        assertThat(response.getHeader(TraceIdFilter.TRACE_HEADER)).isEqualTo(traceInChain.get());
    }

    /** 追踪器必须延迟到请求阶段解析，避免过滤器注册时抢先创建不完整导出链。 */
    @Test
    void shouldResolveTracerLazilyForEachRequest() throws Exception {
        Tracer tracer = mock(Tracer.class);
        AtomicInteger resolutions = new AtomicInteger();
        TraceIdFilter filter = new TraceIdFilter(() -> {
            resolutions.incrementAndGet();
            return tracer;
        });
        MockHttpServletRequest request = new MockHttpServletRequest();
        MockHttpServletResponse response = new MockHttpServletResponse();

        assertThat(resolutions).hasValue(0);
        filter.doFilter(request, response, (ignoredRequest, ignoredResponse) -> { });

        assertThat(resolutions).hasValue(1);
    }
}
