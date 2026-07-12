package com.omni.auth.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;

/**
 * 内部 API 认证过滤器。
 * <p>校验 {@code X-Internal-Token} 请求头，验证通过后设置内部服务认证身份。
 * 仅作用于 {@code /internal/**} 路径。</p>
 *
 * @author Omni-Stack Team
 */
@Slf4j
@Component
public class InternalApiFilter extends OncePerRequestFilter {

    private static final String INTERNAL_TOKEN_HEADER = "X-Internal-Token";
    private static final String INTERNAL_PATH_PREFIX = "/internal/";

    @Value("${omni.internal.api.token:}")
    private String expectedToken;

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return !request.getRequestURI().startsWith(INTERNAL_PATH_PREFIX);
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        String token = request.getHeader(INTERNAL_TOKEN_HEADER);

        if (expectedToken == null || expectedToken.isBlank()) {
            log.warn("Internal API token not configured, rejecting request to {}", request.getRequestURI());
            writeError(response, HttpServletResponse.SC_INTERNAL_SERVER_ERROR, "Internal API not configured");
            return;
        }

        if (token == null || !token.equals(expectedToken)) {
            log.warn("Invalid internal token for request to {}", request.getRequestURI());
            writeError(response, HttpServletResponse.SC_UNAUTHORIZED, "Invalid internal token");
            return;
        }

        // 设置内部服务认证身份（具有 INTERNAL_SERVICE 权限）
        UsernamePasswordAuthenticationToken auth = new UsernamePasswordAuthenticationToken(
                "internal-service", null,
                List.of(new SimpleGrantedAuthority("ROLE_INTERNAL_SERVICE"))
        );
        SecurityContextHolder.getContext().setAuthentication(auth);

        filterChain.doFilter(request, response);
    }

    private void writeError(HttpServletResponse response, int status, String message) throws IOException {
        response.setStatus(status);
        response.setContentType("application/json;charset=UTF-8");
        response.getWriter().write("{\"code\":" + status + ",\"message\":\"" + message + "\",\"data\":null}");
    }
}
