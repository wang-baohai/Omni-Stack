package com.omni.srm.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.omni.common.core.result.R;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * SRM 租户上下文过滤器，业务请求缺少合法身份头时失败关闭。
 *
 * @author Omni-Stack Team
 */
@Component
@RequiredArgsConstructor
public class SrmTenantContextFilter extends OncePerRequestFilter {

    private final ObjectMapper objectMapper;

    /** {@inheritDoc} */
    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        String uri = request.getRequestURI();
        if (uri.startsWith("/actuator") || uri.startsWith("/error")) {
            filterChain.doFilter(request, response);
            return;
        }
        try {
            Long userId = parsePositive(request.getHeader("X-User-Id"));
            Long tenantId = parsePositive(request.getHeader("X-Tenant-Id"));
            if (userId == null || tenantId == null) {
                response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
                response.setContentType(MediaType.APPLICATION_JSON_VALUE);
                response.setCharacterEncoding("UTF-8");
                objectMapper.writeValue(response.getWriter(), R.fail(401, "缺少合法的用户或租户身份"));
                return;
            }
            SrmTenantContext.set(new SrmTenantContext.RequestIdentity(
                    userId, tenantId, request.getHeader("X-User-Name")));
            filterChain.doFilter(request, response);
        } finally {
            SrmTenantContext.clear();
        }
    }

    /** {@inheritDoc} */
    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return request.getRequestURI().startsWith("/api/internal/");
    }

    private Long parsePositive(String value) {
        try {
            Long parsed = Long.valueOf(value);
            return parsed > 0 ? parsed : null;
        } catch (NumberFormatException | NullPointerException ignored) {
            return null;
        }
    }
}
