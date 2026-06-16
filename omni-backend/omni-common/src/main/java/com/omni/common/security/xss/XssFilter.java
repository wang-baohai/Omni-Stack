package com.omni.common.security.xss;

import com.omni.common.core.security.XssConfigProvider;
import com.omni.common.core.security.XssSettings;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * XSS 防护 Servlet 过滤器。
 * <p>
 * 从请求头 {@code X-Tenant-Id} 获取租户标识，通过 {@link XssConfigProvider}
 * 查询该租户的 XSS 防护配置。若防护已启用且存在规则，则将请求包装为
 * {@link XssHttpServletRequestWrapper} 并将规则设置到 {@link XssRuleHolder}，
 * 供查询参数净化和 Jackson JSON Body 净化使用。
 * </p>
 */
@Slf4j
@RequiredArgsConstructor
public class XssFilter extends OncePerRequestFilter {

    private final XssConfigProvider xssConfigProvider;

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain)
            throws ServletException, IOException {
        // 从网关注入的请求头获取租户ID
        String tenantIdStr = request.getHeader("X-Tenant-Id");
        if (tenantIdStr == null || tenantIdStr.isBlank()) {
            filterChain.doFilter(request, response);
            return;
        }

        Long tenantId;
        try {
            tenantId = Long.parseLong(tenantIdStr);
        } catch (NumberFormatException e) {
            log.warn("无效的 X-Tenant-Id 请求头: {}", tenantIdStr);
            filterChain.doFilter(request, response);
            return;
        }

        XssSettings settings = xssConfigProvider.getXssSettings(tenantId);
        if (settings == null || !settings.isEnabled()
                || settings.getRules() == null || settings.getRules().isEmpty()) {
            filterChain.doFilter(request, response);
            return;
        }

        // 设置 ThreadLocal 供 XssStringDeserializer 使用
        XssRuleHolder.set(settings.getRules());
        try {
            // 包装请求以净化查询参数
            XssHttpServletRequestWrapper wrappedRequest =
                    new XssHttpServletRequestWrapper(request, settings.getRules());
            filterChain.doFilter(wrappedRequest, response);
        } finally {
            // 防止 ThreadLocal 内存泄漏
            XssRuleHolder.clear();
        }
    }
}
