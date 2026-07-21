package com.omni.srm.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * 将 Gateway 注入的身份头转换为 Spring Security 认证对象。
 *
 * @author Omni-Stack Team
 */
@Slf4j
public class GatewayPreAuthFilter extends OncePerRequestFilter {

    /** {@inheritDoc} */
    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        String uri = request.getRequestURI();
        if (!uri.startsWith("/actuator") && !uri.startsWith("/error")
                && !"true".equals(request.getHeader("X-Gateway-Forwarded"))) {
            response.setStatus(HttpServletResponse.SC_FORBIDDEN);
            response.setContentType("application/json;charset=UTF-8");
            response.getWriter().write("{\"code\":403,\"message\":\"禁止直接访问 SRM 服务\",\"data\":null}");
            return;
        }
        String userId = request.getHeader("X-User-Id");
        if (userId == null || userId.isBlank()) {
            filterChain.doFilter(request, response);
            return;
        }
        List<SimpleGrantedAuthority> authorities = new ArrayList<>();
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
        return request.getRequestURI().startsWith("/api/internal/");
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
