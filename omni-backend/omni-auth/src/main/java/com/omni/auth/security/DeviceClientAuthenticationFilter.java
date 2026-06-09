package com.omni.auth.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpMethod;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.core.ClientAuthenticationMethod;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.server.authorization.authentication.OAuth2ClientAuthenticationToken;
import org.springframework.security.oauth2.server.authorization.client.RegisteredClient;
import org.springframework.security.oauth2.server.authorization.client.RegisteredClientRepository;
import org.springframework.security.web.servlet.util.matcher.PathPatternRequestMatcher;
import org.springframework.security.web.util.matcher.OrRequestMatcher;
import org.springframework.security.web.util.matcher.RequestMatcher;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * 设备授权流程公有客户端认证过滤器。
 * <p>
 * 解决 SAS 7 中 {@code PublicClientAuthenticationConverter} 仅处理 PKCE token 请求、
 * 不处理设备授权相关端点公有客户端认证的问题。
 * 本过滤器覆盖两个场景：
 * </p>
 * <ol>
 *   <li>{@code POST /oauth2/device_authorization} — 设备码申请阶段</li>
 *   <li>{@code POST /oauth2/token}（grant_type=device_code）— 设备码轮询 token 阶段</li>
 * </ol>
 * <p>
 * 过滤器在 {@code SecurityContextPersistenceFilter} 之后执行，
 * 将已认证的客户端主体写入 {@link SecurityContextHolder}，
 * 使后续 SAS 端点过滤器能正确获取已认证的客户端。
 * </p>
 *
 * @author Omni-Stack Team
 * @see org.springframework.security.oauth2.server.authorization.web.OAuth2DeviceAuthorizationEndpointFilter
 * @see org.springframework.security.oauth2.server.authorization.web.OAuth2TokenEndpointFilter
 */
@Slf4j
@RequiredArgsConstructor
public class DeviceClientAuthenticationFilter extends OncePerRequestFilter {

    /** RFC 8628 设备码授权类型 */
    private static final String DEVICE_CODE_GRANT_TYPE =
            "urn:ietf:params:oauth:grant-type:device_code";

    /** 匹配 POST /oauth2/device_authorization 和 POST /oauth2/token */
    private static final RequestMatcher DEVICE_ENDPOINTS_MATCHER = new OrRequestMatcher(
            PathPatternRequestMatcher.withDefaults()
                    .matcher(HttpMethod.POST, "/oauth2/device_authorization"),
            PathPatternRequestMatcher.withDefaults()
                    .matcher(HttpMethod.POST, "/oauth2/token")
    );

    /** OAuth2 客户端注册仓储，用于按 clientId 查找已注册的客户端配置 */
    private final RegisteredClientRepository registeredClientRepository;

    /**
     * 执行设备授权流程的公有客户端认证逻辑。
     * <p>
     * 仅处理 {@link #DEVICE_ENDPOINTS_MATCHER} 匹配的请求。
     * 对于 {@code /oauth2/token} 端点，进一步校验 {@code grant_type} 是否为设备码类型，
     * 非设备码请求（如 authorization_code、refresh_token）直接放行。
     * 认证成功后将 {@link OAuth2ClientAuthenticationToken} 写入 {@link SecurityContextHolder}。
     * </p>
     *
     * @param request     HTTP 请求
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
        if (!DEVICE_ENDPOINTS_MATCHER.matches(request)) {
            filterChain.doFilter(request, response);
            return;
        }

        // 对于 /oauth2/token 端点，仅处理 device_code 授权类型
        String grantType = request.getParameter("grant_type");
        boolean isTokenEndpoint = request.getRequestURI().endsWith("/oauth2/token");
        if (isTokenEndpoint && !DEVICE_CODE_GRANT_TYPE.equals(grantType)) {
            // 非设备码轮询请求（如 authorization_code、refresh_token），跳过
            filterChain.doFilter(request, response);
            return;
        }

        try {
            // 从请求参数中提取 client_id（兼容 form body 和 query string）
            String clientId = request.getParameter("client_id");
            if (clientId == null || clientId.isBlank()) {
                throw new OAuth2AuthenticationException(
                        new OAuth2Error("invalid_request", "client_id is required", null));
            }

            RegisteredClient client = registeredClientRepository.findByClientId(clientId);
            if (client == null) {
                throw new OAuth2AuthenticationException(
                        new OAuth2Error("invalid_client", "Client not found: " + clientId, null));
            }

            if (!client.getClientAuthenticationMethods()
                    .contains(ClientAuthenticationMethod.NONE)) {
                throw new OAuth2AuthenticationException(
                        new OAuth2Error("invalid_client",
                                "Client '" + clientId + "' is not a public client", null));
            }

            // 使用已注册客户端构造已认证的客户端 Token（构造函数内部调用 setAuthenticated(true)）
            OAuth2ClientAuthenticationToken clientAuth =
                    new OAuth2ClientAuthenticationToken(
                            client, ClientAuthenticationMethod.NONE, null);

            SecurityContext context = SecurityContextHolder.getContext();
            context.setAuthentication(clientAuth);

            log.debug("公有客户端 '{}' 已通过设备授权认证（端点: {}）", clientId, request.getRequestURI());
        } catch (OAuth2AuthenticationException ex) {
            log.warn("设备授权客户端认证失败: {}", ex.getError().getDescription());
            response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
            response.setContentType("application/json;charset=UTF-8");
            response.getWriter().write(
                    "{\"error\":\"" + ex.getError().getErrorCode() + "\"}");
            return;
        }

        filterChain.doFilter(request, response);
    }
}
