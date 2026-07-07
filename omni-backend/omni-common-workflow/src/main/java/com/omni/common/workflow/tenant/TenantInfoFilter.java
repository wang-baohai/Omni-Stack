package com.omni.common.workflow.tenant;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * 租户信息过滤器。
 * <p>
 * 从 Gateway 转发的 {@code X-Tenant-Id} 请求头中提取租户 ID，
 * 写入 {@link TenantInfoHolder}（ThreadLocal），供 Flowable 引擎
 * 在部署和运行时自动使用租户标识。</p>
 * <p>
 * 请求结束后在 {@code finally} 块中清除 ThreadLocal，防止内存泄漏。
 * </p>
 *
 * @author Omni-Stack Team
 * @see TenantInfoHolder
 */
@Slf4j
public class TenantInfoFilter extends OncePerRequestFilter {

    private static final String HEADER_TENANT_ID = "X-Tenant-Id";

    /**
     * 执行租户信息过滤。
     * <p>
     * 从请求头提取 {@code X-Tenant-Id} 并设置到 {@link TenantInfoHolder}，
     * 请求结束后清除 ThreadLocal。
     * </p>
     *
     * @param request     当前 HTTP 请求
     * @param response    当前 HTTP 响应
     * @param filterChain 过滤器链
     * @throws ServletException Servlet 处理异常
     * @throws IOException      I/O 处理异常
     */
    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain)
            throws ServletException, IOException {
        String tenantId = request.getHeader(HEADER_TENANT_ID);
        if (tenantId != null && !tenantId.isBlank()) {
            TenantInfoHolder.setTenantId(tenantId);
            log.debug("租户信息设置: tenantId={}, URI={}", tenantId, request.getRequestURI());
        }
        try {
            filterChain.doFilter(request, response);
        } finally {
            TenantInfoHolder.clear();
        }
    }
}
