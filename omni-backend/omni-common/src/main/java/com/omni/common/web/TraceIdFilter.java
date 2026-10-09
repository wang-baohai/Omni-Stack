package com.omni.common.web;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import io.micrometer.tracing.Span;
import io.micrometer.tracing.Tracer;
import org.slf4j.MDC;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.UUID;
import java.util.function.Supplier;
import java.util.regex.Pattern;

/**
 * Servlet 请求关联 ID 过滤器。
 * <p>复用网关生成的合法追踪号，并将其写入 MDC、下游 Feign 请求和响应头。</p>
 */
public class TraceIdFilter extends OncePerRequestFilter {

    /** 统一追踪号请求与响应头。 */
    public static final String TRACE_HEADER = "X-Trace-Id";
    /** 日志 MDC 字段名。 */
    public static final String MDC_KEY = "traceId";
    private static final Pattern SAFE_TRACE_ID = Pattern.compile("[A-Za-z0-9-]{16,64}");
    private final Supplier<Tracer> tracerSupplier;

    /** 无追踪运行时场景使用，仅保留兼容关联 ID。 */
    public TraceIdFilter() {
        this(() -> null);
    }

    /**
     * 创建请求关联过滤器。
     *
     * @param tracer Micrometer 追踪器；观测关闭时可为空
     */
    public TraceIdFilter(Tracer tracer) {
        this(() -> tracer);
    }

    /** 延迟获取追踪器，避免 Servlet 过滤器注册阶段过早初始化追踪导出链。 */
    TraceIdFilter(Supplier<Tracer> tracerSupplier) {
        this.tracerSupplier = tracerSupplier;
    }

    /** {@inheritDoc} */
    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain filterChain)
            throws ServletException, IOException {
        String traceId = resolveTraceId(request.getHeader(TRACE_HEADER));
        response.setHeader(TRACE_HEADER, traceId);
        MDC.put(MDC_KEY, traceId);
        try {
            filterChain.doFilter(request, response);
        } finally {
            MDC.remove(MDC_KEY);
        }
    }

    /** 优先使用 W3C 上下文对应的真实 traceId，关闭追踪时兼容旧请求头。 */
    private String resolveTraceId(String incoming) {
        Tracer tracer = tracerSupplier.get();
        Span currentSpan = tracer == null ? null : tracer.currentSpan();
        if (currentSpan != null && currentSpan.context() != null) {
            return currentSpan.context().traceId();
        }
        return incoming != null && SAFE_TRACE_ID.matcher(incoming).matches()
                ? incoming : UUID.randomUUID().toString().replace("-", "");
    }
}
