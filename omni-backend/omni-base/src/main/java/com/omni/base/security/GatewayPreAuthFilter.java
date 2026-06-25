package com.omni.base.security;

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
 *   <li>{@code X-User-Scopes} — 空格分隔的权限编码（如 {@code dict:type:list dict:data:create}）</li>
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

    /**
     * 执行网关预认证过滤逻辑。
     * <p>
     * 从请求头中提取用户身份和权限信息，构建 {@code Authentication} 对象
     * 并设置到 {@link SecurityContextHolder} 中。若缺少 {@code X-User-Id} 请求头，
     * 则跳过认证直接放行。
     * </p>
     *
     * @param request     当前 HTTP 请求
     * @param response    当前 HTTP 响应
     * @param filterChain 过滤器链，用于继续执行后续过滤器
     * @throws ServletException Servlet 处理异常
     * @throws IOException      I/O 处理异常
     */
    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain)
            throws ServletException, IOException {
        String userId = request.getHeader(HEADER_USER_ID);
        if (userId == null || userId.isBlank()) {
            log.debug("网关预认证跳过：无 X-User-Id 头, URI={}", request.getRequestURI());
            filterChain.doFilter(request, response);
            return;
        }

        String userName = request.getHeader(HEADER_USER_NAME);
        String principal = userName != null ? userName : userId;

        List<SimpleGrantedAuthority> authorities = new ArrayList<>();

        // 解析角色头：逗号分隔，添加 ROLE_ 前缀
        String rolesHeader = request.getHeader(HEADER_USER_ROLES);
        if (rolesHeader != null && !rolesHeader.isBlank()) {
            for (String role : rolesHeader.split(",")) {
                String trimmed = role.trim();
                if (!trimmed.isEmpty()) {
                    authorities.add(new SimpleGrantedAuthority("ROLE_" + trimmed));
                }
            }
        }

        // 解析权限头：逗号或空格分隔
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
}
