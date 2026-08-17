package com.omni.common.web;

import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

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
}
