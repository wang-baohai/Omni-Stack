package com.omni.common.service.identity;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.omni.common.core.result.R;
import com.omni.common.service.config.ServiceIdentityProperties;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * 将可信 Gateway 身份头转换为 Spring Security 认证对象。
 *
 * @author Omni-Stack Team
 */
@RequiredArgsConstructor
public class GatewayPreAuthenticationFilter extends OncePerRequestFilter {

    private final ServiceIdentityProperties properties;
    private final ServicePathPolicy pathPolicy;
    private final ObjectMapper objectMapper;

    /** {@inheritDoc} */
    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        String uri = request.getRequestURI();
        if (!pathPolicy.isPublicOrManagement(uri)
                && !"true".equals(request.getHeader("X-Gateway-Forwarded"))) {
            response.setStatus(HttpServletResponse.SC_FORBIDDEN);
            response.setContentType(MediaType.APPLICATION_JSON_VALUE);
            response.setCharacterEncoding("UTF-8");
            objectMapper.writeValue(response.getWriter(), R.fail(403,
                    "禁止直接访问 " + properties.getDisplayName() + " 服务"));
            return;
        }
        String userId = request.getHeader("X-User-Id");
        if (userId == null || userId.isBlank()) {
            filterChain.doFilter(request, response);
            return;
        }
        List<SimpleGrantedAuthority> authorities = new ArrayList<>(16);
        addAuthorities(authorities, request.getHeader("X-User-Roles"), true);
        addAuthorities(authorities, request.getHeader("X-User-Scopes"), false);
        String username = request.getHeader("X-User-Name");
        UsernamePasswordAuthenticationToken authentication = new UsernamePasswordAuthenticationToken(
                username == null || username.isBlank() ? userId : username, null, authorities);
        SecurityContextHolder.getContext().setAuthentication(authentication);
        try {
            filterChain.doFilter(request, response);
        } finally {
            SecurityContextHolder.clearContext();
        }
    }

    /** {@inheritDoc} */
    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return pathPolicy.isInternal(request.getRequestURI());
    }

    private void addAuthorities(List<SimpleGrantedAuthority> authorities, String header, boolean role) {
        if (header == null || header.isBlank()) {
            return;
        }
        for (String value : header.split("[,\\s]+")) {
            if (!value.isBlank()) {
                authorities.add(new SimpleGrantedAuthority(role ? "ROLE_" + value : value));
            }
        }
    }
}
