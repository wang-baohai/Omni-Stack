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

    /** Gateway 转发标记头，缺失则说明请求未经 Gateway，可能是直接访问 */
    private static final String HEADER_GATEWAY_FORWARDED = "X-Gateway-Forwarded";

    /**
     * 执行网关预认证过滤逻辑。
     * <p>
     * 首先校验 {@code X-Gateway-Forwarded} 请求头，拒绝未经 Gateway 转发的直接访问。
     * 然后从请求头中提取用户身份和权限信息，构建 {@code Authentication} 对象
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
        // 校验 Gateway 转发标记，拒绝直接访问后端服务的请求
        String gatewayForwarded = request.getHeader(HEADER_GATEWAY_FORWARDED);
        if (!"true".equals(gatewayForwarded)) {
            String uri = request.getRequestURI();
            // actuator 和 error 路径允许直接访问（健康检查等运维场景）
            if (!uri.startsWith("/actuator") && !uri.startsWith("/error")) {
                log.warn("拒绝未经 Gateway 的直接访问: URI={}", uri);
                response.setStatus(HttpServletResponse.SC_FORBIDDEN);
                response.setContentType("application/json;charset=UTF-8");
                response.getWriter().write(
                        "{\"code\":403,\"message\":\"禁止直接访问后端服务，请通过 Gateway 访问\",\"data\":null}");
                return;
            }
        }

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
