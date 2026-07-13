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
 * 网关预认证过滤器，负责将 Gateway 转发的用户身份请求头转换为 Spring Security {@link Authentication} 对象。
 *
 * <h3>在安全过滤器链中的位置</h3>
 * <p>本过滤器位于 Spring Security 过滤器链的前端，在 {@code SecurityContextPersistenceFilter} 之后、
 * {@link DataScopeResolveFilter} 之前执行。Gateway 在完成 JWT 验证后，
 * 将用户身份信息注入到转发请求的 HTTP 头中，本过滤器将其解析并写入
 * {@link SecurityContextHolder}，使后续所有安全组件（包括 {@code @PreAuthorize} 注解）
 * 能够正确执行授权检查。</p>
 *
 * <h3>请求头约定</h3>
 * <p>Gateway 在验证 JWT 后会注入以下请求头：</p>
 * <ul>
 *   <li>{@code X-User-Id} — 用户 ID（必需，缺失时跳过认证）</li>
 *   <li>{@code X-User-Name} — 用户名（用作 principal，缺失时回退为 userId）</li>
 *   <li>{@code X-Tenant-Id} — 租户 ID（多租户场景下标识当前租户）</li>
 *   <li>{@code X-User-Roles} — 逗号分隔的角色编码（如 {@code SUPER_ADMIN}），
 *       自动添加 {@code ROLE_} 前缀以支持 {@code hasRole()} 检查</li>
 *   <li>{@code X-User-Scopes} — 逗号或空格分隔的权限编码
 *       （如 {@code system:user:list system:role:create}），直接作为 authority 支持
 *       {@code hasAuthority()} 检查</li>
 * </ul>
 *
 * <h3>认证重建策略</h3>
 * <p>始终以 Gateway 注入的请求头为准重建 {@link Authentication}，
 * 即使 {@link SecurityContextHolder} 中已存在认证信息（如 HttpSession 缓存的旧认证）。
 * 这确保 {@code @PreAuthorize} 始终基于最新的权限信息进行授权检查。</p>
 *
 * @author Omni-Stack Team
 * @see DataScopeResolveFilter
 * @see org.springframework.security.core.context.SecurityContextHolder
 * @see org.springframework.security.web.context.SecurityContextPersistenceFilter
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

    /** 内部服务接口路径前缀，由独立的内部令牌过滤器负责认证 */
    private static final String INTERNAL_PATH_PREFIX = "/internal/";

    /**
     * 内部服务接口不执行网关预认证，避免覆盖内部服务身份或误判为非法直连。
     *
     * @param request HTTP 请求
     * @return 内部接口返回 true，其余请求返回 false
     */
    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return request.getRequestURI().startsWith(INTERNAL_PATH_PREFIX);
    }

    /**
     * 执行网关预认证逻辑。
     *
     * <p>从请求头中提取用户 ID、用户名、角色和权限信息，构建
     * {@link UsernamePasswordAuthenticationToken} 并写入 {@link SecurityContextHolder}。
     * 无 {@code X-User-Id} 头时直接放行，不设置认证信息。</p>
     *
     * @param request     HTTP 请求，需包含 Gateway 注入的身份请求头
     * @param response    HTTP 响应
     * @param filterChain 过滤器链
     * @throws ServletException Servlet 异常
     * @throws IOException      IO 异常
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

    /**
     * 获取请求头名称列表，用于诊断日志输出。
     *
     * @param request HTTP 请求
     * @return 逗号分隔的请求头名称字符串
     */
    private static String getHeaderNames(HttpServletRequest request) {
        return String.join(", ", Collections.list(request.getHeaderNames()));
    }
}
