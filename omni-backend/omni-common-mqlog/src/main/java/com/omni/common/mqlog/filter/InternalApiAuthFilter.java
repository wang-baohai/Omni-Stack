package com.omni.common.mqlog.filter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.List;

/**
 * 内部 API 认证过滤器。
 * <p>
 * 拦截所有 {@code /api/internal/} 路径的请求，校验 {@code X-Internal-Token} 请求头
 * 是否与配置的共享密钥匹配。不匹配则返回 403，防止内部 API 被外部直接调用。
 * </p>
 *
 * @author Omni-Stack Team
 */
@Slf4j
public class InternalApiAuthFilter extends OncePerRequestFilter {

    private static final String HEADER_INTERNAL_TOKEN = "X-Internal-Token";
    private static final String INTERNAL_PATH_PREFIX = "/api/internal/";

    private final String expectedToken;

    public InternalApiAuthFilter(String expectedToken) {
        this.expectedToken = expectedToken;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return !request.getRequestURI().startsWith(INTERNAL_PATH_PREFIX);
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain)
            throws ServletException, IOException {
        String token = request.getHeader(HEADER_INTERNAL_TOKEN);
        if (expectedToken == null || expectedToken.isBlank()) {
            log.error("内部 API 共享密钥未配置，拒绝访问: URI={}", request.getRequestURI());
            writeError(response, HttpServletResponse.SC_SERVICE_UNAVAILABLE, "内部 API 未配置");
            return;
        }
        if (!tokenMatches(token)) {
            log.warn("内部 API 认证失败: URI={}, token={}", request.getRequestURI(),
                    token != null ? "***" : "missing");
            writeError(response, HttpServletResponse.SC_UNAUTHORIZED, "内部 API 认证失败");
            return;
        }

        UsernamePasswordAuthenticationToken authentication = new UsernamePasswordAuthenticationToken(
                "internal-service", null,
                List.of(new SimpleGrantedAuthority("ROLE_INTERNAL_SERVICE")));
        SecurityContextHolder.getContext().setAuthentication(authentication);
        try {
            filterChain.doFilter(request, response);
        } finally {
            SecurityContextHolder.clearContext();
        }
    }

    private boolean tokenMatches(String token) {
        if (token == null) {
            return false;
        }
        return MessageDigest.isEqual(
                token.getBytes(StandardCharsets.UTF_8),
                expectedToken.getBytes(StandardCharsets.UTF_8));
    }

    private void writeError(HttpServletResponse response, int status, String message) throws IOException {
        response.setStatus(status);
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        response.getWriter().write(
                "{\"code\":" + status + ",\"message\":\"" + message + "\",\"data\":null}");
    }
}
