package com.omni.common.service.internal;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.omni.common.core.result.R;
import com.omni.common.service.config.ServiceIdentityProperties;
import com.omni.common.service.identity.ServicePathPolicy;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
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
 * 校验内部 API 共享密钥并建立内部服务身份。
 *
 * @author Omni-Stack Team
 */
@Slf4j
@RequiredArgsConstructor
public class InternalApiAuthenticationFilter extends OncePerRequestFilter {

    private static final String HEADER_INTERNAL_TOKEN = "X-Internal-Token";

    private final ServiceIdentityProperties properties;
    private final ServicePathPolicy pathPolicy;
    private final ObjectMapper objectMapper;

    /** {@inheritDoc} */
    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return !pathPolicy.isInternal(request.getRequestURI());
    }

    /** {@inheritDoc} */
    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        String expectedToken = properties.getInternalApi().getToken();
        String token = request.getHeader(HEADER_INTERNAL_TOKEN);
        if (!tokenMatches(token, expectedToken)) {
            log.warn("内部 API 认证失败: service={}, uri={}, token={}", properties.getName(),
                    request.getRequestURI(), token == null ? "missing" : "***");
            writeError(response, HttpServletResponse.SC_UNAUTHORIZED, "内部 API 认证失败");
            return;
        }
        UsernamePasswordAuthenticationToken authentication = new UsernamePasswordAuthenticationToken(
                "internal-service", null, List.of(new SimpleGrantedAuthority("ROLE_INTERNAL_SERVICE")));
        SecurityContextHolder.getContext().setAuthentication(authentication);
        try {
            filterChain.doFilter(request, response);
        } finally {
            SecurityContextHolder.clearContext();
        }
    }

    private boolean tokenMatches(String token, String expectedToken) {
        return token != null && expectedToken != null && MessageDigest.isEqual(
                token.getBytes(StandardCharsets.UTF_8), expectedToken.getBytes(StandardCharsets.UTF_8));
    }

    private void writeError(HttpServletResponse response, int status, String message) throws IOException {
        response.setStatus(status);
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        objectMapper.writeValue(response.getWriter(), R.fail(status, message));
    }
}
