package com.omni.auth.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * 网关预认证过滤器。
 * <p>
 * 从 Gateway 转发的请求头中提取用户身份和权限信息，构建 Spring Security
 * {@link org.springframework.security.core.Authentication} 对象，
 * 使 {@code @PreAuthorize} 方法级权限注解能够正确执行授权检查。
 * </p>
 * <p>
 * Gateway 在验证 JWT 后会注入以下请求头：
 * </p>
 * <ul>
 *   <li>{@code X-User-Id} — 用户 ID</li>
 *   <li>{@code X-User-Name} — 用户名</li>
 *   <li>{@code X-Tenant-Id} — 租户 ID</li>
 *   <li>{@code X-User-Roles} — 逗号分隔的角色编码（如 {@code SUPER_ADMIN}）</li>
 *   <li>{@code X-User-Scopes} — 空格分隔的权限编码（如 {@code system:user:list system:role:create}）</li>
 * </ul>
 *
 * @author Omni-Stack Team
 */
@Slf4j
public class GatewayPreAuthFilter extends OncePerRequestFilter {

    private static final String HEADER_USER_ID = "X-User-Id";
    private static final String HEADER_USER_NAME = "X-User-Name";
    private static final String HEADER_TENANT_ID = "X-Tenant-Id";
    private static final String HEADER_USER_ROLES = "X-User-Roles";
    private static final String HEADER_USER_SCOPES = "X-User-Scopes";

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain)
            throws ServletException, IOException {
        // 仅处理由 Gateway 转发的请求（包含 X-User-Id 头）
        String userId = request.getHeader(HEADER_USER_ID);
        if (userId == null || userId.isBlank()) {
            log.info("网关预认证跳过：无 X-User-Id 头, URI={}, headers=[{}]",
                    request.getRequestURI(), getHeaderNames(request));
            filterChain.doFilter(request, response);
            return;
        }

        log.info("网关预认证开始：userId={}, URI={}", userId, request.getRequestURI());

        // 始终以 Gateway 注入的认证信息为准（Gateway 已完成 JWT 验证）。
        // 不跳过已有的 SecurityContext 认证，因为 HttpSession 可能缓存了旧认证，
        // 导致 @PreAuthorize 因权限信息过期而校验失败。

        String userName = request.getHeader(HEADER_USER_NAME);
        String principal = userName != null ? userName : userId;

        List<SimpleGrantedAuthority> authorities = new ArrayList<>();

        // 解析角色头：逗号分隔，添加 ROLE_ 前缀（与 OmniUserDetailsService 约定一致）
        String rolesHeader = request.getHeader(HEADER_USER_ROLES);
        if (rolesHeader != null && !rolesHeader.isBlank()) {
            for (String role : rolesHeader.split(",")) {
                String trimmed = role.trim();
                if (!trimmed.isEmpty()) {
                    authorities.add(new SimpleGrantedAuthority("ROLE_" + trimmed));
                }
            }
        }

        // 解析权限头：逗号或空格分隔，直接作为 authority（与 @PreAuthorize hasAuthority 匹配）
        // 注意：Gateway AuthFilter 的 getClaimAsString 对 List 类型 claim 使用逗号分隔，
        // 而 OAuth2 标准 scope claim 使用空格分隔，因此需要同时支持两种分隔符。
        String scopesHeader = request.getHeader(HEADER_USER_SCOPES);
        if (scopesHeader != null && !scopesHeader.isBlank()) {
            for (String scope : scopesHeader.split("[,\\s]+")) {
                if (!scope.isEmpty()) {
                    authorities.add(new SimpleGrantedAuthority(scope));
                }
            }
        }

        UsernamePasswordAuthenticationToken authentication =
                new UsernamePasswordAuthenticationToken(principal, null, authorities);
        SecurityContextHolder.getContext().setAuthentication(authentication);

        log.debug("网关预认证：user={}, tenant={}, roles={}, scopes={}",
                principal, request.getHeader(HEADER_TENANT_ID), rolesHeader, scopesHeader);

        filterChain.doFilter(request, response);
    }

    /** 获取请求头名称列表（诊断用） */
    private static String getHeaderNames(HttpServletRequest request) {
        return String.join(", ", Collections.list(request.getHeaderNames()));
    }
}
