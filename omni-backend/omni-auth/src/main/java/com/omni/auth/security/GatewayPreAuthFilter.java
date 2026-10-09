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
import java.nio.charset.StandardCharsets;
import java.security.InvalidKeyException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Base64;
import java.util.Collections;
import java.util.List;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

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
 *   <li>{@code X-Gateway-Assertion} — 网关用共享密钥对身份签发的 HMAC-SHA256 断言</li>
 * </ul>
 *
 * <h3>身份可信度判定</h3>
 * <p>
 * 明文身份头无法阻止“直连服务端口伪造请求头”的绕过，因此本过滤器优先验证
 * {@code X-Gateway-Assertion} 签名与时效窗口（±5 分钟）：断言有效时以 payload 身份为准；
 * 断言签名无效或过期返回 401；密钥已配置但断言缺失而携带身份头时同样返回 401；
 * 密钥未配置时同样 401 拒绝（fail-closed），不再信任明文身份头。
 * </p>
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

    /** Gateway 签名断言头，防伪造身份的信任凭证 */
    private static final String HEADER_GATEWAY_ASSERTION = "X-Gateway-Assertion";

    /** 断言头格式：{@code Base64(payload) + ":" + Base64(HMAC)} */
    private static final String ASSERTION_SEPARATOR = ":";

    /** 断言 payload 分隔正则：{@code userId|tenantId|username|issuedAtEpochSecond} */
    private static final String ASSERTION_PAYLOAD_SPLIT = "\\|";

    /** 断言签名算法 */
    private static final String ASSERTION_HMAC_ALGORITHM = "HmacSHA256";

    /** 密钥环境变量回退名（方便容器直接注入，无需改 application.yml） */
    private static final String ASSERTION_SECRET_ENV = "GATEWAY_ASSERTION_SECRET";

    /** 断言时间戳允许偏差（秒），超出视为过期 */
    private static final long ASSERTION_TTL_SECONDS = 300L;

    /** HMAC 密钥推荐最小长度（字节），低于此强度仅告警不拒绝，由网关侧 fail-closed 保证 */
    private static final int MIN_ASSERTION_SECRET_BYTES = 32;

    private static final String ASSERTION_INVALID_MESSAGE = "网关签名断言无效";
    private static final String ASSERTION_EXPIRED_MESSAGE = "网关签名断言过期，请重试";
    private static final String ASSERTION_MISSING_MESSAGE = "缺少网关签名断言，请通过 Gateway 访问";

    /**
     * 网关签名断言密钥。由 SecurityConfig 通过 {@link #setAssertionSecret(String)} 传入；
     * 未传入时回退环境变量 {@code GATEWAY_ASSERTION_SECRET}。
     */
    private String assertionSecret;

    /**
     * 设置网关签名断言密钥（由 SecurityConfig 在构造过滤器后调用）。
     *
     * @param assertionSecret 共享密钥，为空表示不启用断言校验
     */
    public void setAssertionSecret(String assertionSecret) {
        this.assertionSecret = assertionSecret;
    }

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
        String uri = request.getRequestURI();
        // 校验 Gateway 转发标记，拒绝直接访问后端服务的请求
        String gatewayForwarded = request.getHeader(HEADER_GATEWAY_FORWARDED);
        if (!"true".equals(gatewayForwarded)) {
            // actuator 和 error 路径允许直接访问（健康检查等运维场景）
            if (!uri.startsWith("/actuator") && !uri.startsWith("/error")) {
                log.warn("拒绝未经 Gateway 的直接访问: URI={}", uri);
                writeForbidden(response);
                return;
            }
        }

        String assertion = request.getHeader(HEADER_GATEWAY_ASSERTION);
        String secret = resolveAssertionSecret();
        String userId = request.getHeader(HEADER_USER_ID);
        String userName = request.getHeader(HEADER_USER_NAME);
        String tenantId = request.getHeader(HEADER_TENANT_ID);
        boolean identityPresent = userId != null && !userId.isBlank();

        if (assertion != null && !assertion.isBlank()) {
            if (secret.isEmpty()) {
                log.warn("收到 {} 但未配置网关断言密钥（omni.gateway.assertion-secret / {}），无法验证签名: URI={}",
                        HEADER_GATEWAY_ASSERTION, ASSERTION_SECRET_ENV, uri);
                writeUnauthorized(response, ASSERTION_MISSING_MESSAGE);
                return;
            } else {
                GatewayAssertion verified;
                try {
                    verified = verifyGatewayAssertion(assertion, secret);
                } catch (GatewayAssertionRejectException e) {
                    log.warn("网关签名断言校验失败: URI={}, reason={}", uri, e.getMessage());
                    writeUnauthorized(response, e.getMessage());
                    return;
                }
                // 断言与明文头不一致时直接拒绝：其他可直读 X-Tenant-Id 的组件不应被信任绕过
                if (!matches(verified.userId(), userId) || !matches(verified.tenantId(), tenantId)
                        || !matches(verified.username(), userName)) {
                    log.warn("网关签名断言与明文身份头不一致，拒绝信任: URI={}", uri);
                    writeUnauthorized(response, ASSERTION_INVALID_MESSAGE);
                    return;
                }
                // 断言验证通过：身份以签名 payload 为准，不再依赖可被篡改的明文头
                userId = verified.userId();
                tenantId = verified.tenantId();
                userName = verified.username();
                log.debug("网关签名断言校验通过：user={}, tenant={}, issuedAt={}",
                        userId, tenantId, verified.issuedAt());
            }
        } else if (identityPresent) {
            // D01 修复：移除过渡模式回退，密钥未配置时也拒绝信任明文身份头（fail-closed）
            log.warn("身份头缺少网关签名断言或断言密钥未配置，拒绝信任: URI={}, X-User-Id={}, {}={}",
                    uri, userId, HEADER_GATEWAY_FORWARDED, gatewayForwarded);
            writeUnauthorized(response, ASSERTION_MISSING_MESSAGE);
            return;
        }

        // 仅处理由 Gateway 转发的请求（包含 X-User-Id 头）
        if (userId == null || userId.isBlank()) {
            log.info("网关预认证跳过：无 X-User-Id 头, URI={}, headers=[{}]", uri, getHeaderNames(request));
            filterChain.doFilter(request, response);
            return;
        }

        log.info("网关预认证开始：userId={}, URI={}", userId, uri);

        // 始终以 Gateway 注入的认证信息为准（Gateway 已完成 JWT 验证且断言已验签）。
        // 不跳过已有的 SecurityContext 认证，因为 HttpSession 可能缓存了旧认证，
        // 导致 @PreAuthorize 因权限信息过期而校验失败。

        String principal = userName != null && !userName.isBlank() ? userName : userId;

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
                principal, tenantId, rolesHeader, scopesHeader);

        filterChain.doFilter(request, response);
    }

    /**
     * 解析网关断言密钥：优先 {@link #setAssertionSecret(String)} 传入值，
     * 未传入时回退环境变量 {@code GATEWAY_ASSERTION_SECRET}。
     *
     * @return 去空白后的密钥，未配置时返回空字符串
     */
    private String resolveAssertionSecret() {
        String configured = assertionSecret;
        if (configured == null || configured.isBlank()) {
            configured = System.getenv(ASSERTION_SECRET_ENV);
        }
        return configured == null ? "" : configured.trim();
    }

    /**
     * 验证网关签名断言：校验 HMAC-SHA256 签名与时间戳窗口。
     *
     * @param assertion 断言头值，格式 {@code Base64(payload):Base64(hmac)}
     * @param secret    共享密钥，非空
     * @return 断言中的身份信息
     * @throws GatewayAssertionRejectException 格式错误、签名不匹配、payload 非法或时间戳过期
     */
    private GatewayAssertion verifyGatewayAssertion(String assertion, String secret)
            throws GatewayAssertionRejectException {
        if (secret.getBytes(StandardCharsets.UTF_8).length < MIN_ASSERTION_SECRET_BYTES) {
            log.warn("网关断言密钥长度不足 {} 字节，存在被穷举破解风险，请尽快轮换", MIN_ASSERTION_SECRET_BYTES);
        }
        int separator = assertion.indexOf(ASSERTION_SEPARATOR);
        if (separator <= 0 || separator == assertion.length() - 1) {
            throw new GatewayAssertionRejectException(ASSERTION_INVALID_MESSAGE);
        }
        byte[] payloadBytes = decodeBase64(assertion.substring(0, separator));
        byte[] providedMac = decodeBase64(assertion.substring(separator + 1));
        String payload = new String(payloadBytes, StandardCharsets.UTF_8);
        byte[] expectedMac = hmacSha256(payload, secret);
        // 恒定时间比较，避免通过响应时间逐字推猜签名
        if (!MessageDigest.isEqual(expectedMac, providedMac)) {
            throw new GatewayAssertionRejectException(ASSERTION_INVALID_MESSAGE);
        }
        String[] fields = payload.split(ASSERTION_PAYLOAD_SPLIT, -1);
        if (fields.length < 4) {
            throw new GatewayAssertionRejectException(ASSERTION_INVALID_MESSAGE);
        }
        long issuedAt;
        try {
            issuedAt = Long.parseLong(fields[fields.length - 1].trim());
        } catch (NumberFormatException e) {
            throw new GatewayAssertionRejectException(ASSERTION_INVALID_MESSAGE);
        }
        long now = System.currentTimeMillis() / 1000L;
        if (Math.abs(now - issuedAt) > ASSERTION_TTL_SECONDS) {
            throw new GatewayAssertionRejectException(ASSERTION_EXPIRED_MESSAGE);
        }
        String assertedUserId = fields[0].trim();
        String assertedTenantId = fields[1].trim();
        if (!isPositiveLong(assertedUserId) || !isPositiveLong(assertedTenantId)) {
            throw new GatewayAssertionRejectException(ASSERTION_INVALID_MESSAGE);
        }
        // username 位于第 3 个字段到倒数第 2 个字段之间，允许含 '|'
        String username = String.join("|", Arrays.copyOfRange(fields, 2, fields.length - 1)).trim();
        return new GatewayAssertion(assertedUserId, assertedTenantId, username, issuedAt);
    }

    /**
     * Base64 解码，兼容 URL 安全字母表与标准字母表。
     *
     * @param value 待解码文本
     * @return 解码后的字节数组
     * @throws GatewayAssertionRejectException 不是合法 Base64
     */
    private byte[] decodeBase64(String value) throws GatewayAssertionRejectException {
        try {
            return Base64.getUrlDecoder().decode(value);
        } catch (IllegalArgumentException urlException) {
            try {
                return Base64.getDecoder().decode(value);
            } catch (IllegalArgumentException standardException) {
                throw new GatewayAssertionRejectException(ASSERTION_INVALID_MESSAGE);
            }
        }
    }

    /**
     * 计算断言 payload 的 HMAC-SHA256 签名。
     *
     * @param payload 待签名文本
     * @param secret  共享密钥
     * @return 签名字节数组
     * @throws GatewayAssertionRejectException 算法不可用或密钥非法（fail-closed）
     */
    private byte[] hmacSha256(String payload, String secret) throws GatewayAssertionRejectException {
        try {
            Mac mac = Mac.getInstance(ASSERTION_HMAC_ALGORITHM);
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), ASSERTION_HMAC_ALGORITHM));
            return mac.doFinal(payload.getBytes(StandardCharsets.UTF_8));
        } catch (NoSuchAlgorithmException | InvalidKeyException e) {
            log.error("网关签名断言 HMAC 计算失败：{}", e.getMessage());
            throw new GatewayAssertionRejectException(ASSERTION_INVALID_MESSAGE);
        }
    }

    /**
     * 判断明文头与断言声明的身份是否一致：头缺失或空白视为一致（由断言补齐）。
     *
     * @param asserted 断言中的身份值
     * @param header   明文请求头值，可空
     * @return 一致返回 true
     */
    private boolean matches(String asserted, String header) {
        return header == null || header.isBlank() || header.trim().equals(asserted);
    }

    /**
     * 判断身份字段是否为正整数。
     *
     * @param value 待判断文本
     * @return 正整数返回 true
     */
    private boolean isPositiveLong(String value) {
        if (value == null || value.isBlank()) {
            return false;
        }
        try {
            return Long.parseLong(value) > 0;
        } catch (NumberFormatException e) {
            return false;
        }
    }

    /**
     * 写回 403 拒绝直连响应。
     *
     * @param response HTTP 响应
     * @throws IOException 写出失败
     */
    private void writeForbidden(HttpServletResponse response) throws IOException {
        writeJsonError(response, HttpServletResponse.SC_FORBIDDEN, 403, "禁止直接访问后端服务，请通过 Gateway 访问");
    }

    /**
     * 写回 401 断言校验失败响应。
     *
     * @param response HTTP 响应
     * @param message  错误消息
     * @throws IOException 写出失败
     */
    private void writeUnauthorized(HttpServletResponse response, String message) throws IOException {
        writeJsonError(response, HttpServletResponse.SC_UNAUTHORIZED, 401, message);
    }

    /**
     * 以与 {@code R.fail()} 一致的结构写回 JSON 错误。
     *
     * @param response HTTP 响应
     * @param status   Servlet 状态码
     * @param code     业务错误码
     * @param message  错误消息
     * @throws IOException 写出失败
     */
    private void writeJsonError(HttpServletResponse response, int status, int code, String message)
            throws IOException {
        response.setStatus(status);
        response.setContentType("application/json;charset=UTF-8");
        response.getWriter().write(
                "{\"code\":" + code + ",\"message\":\"" + message + "\",\"data\":null}");
    }

    /**
     * 已验证的网关签名断言内容。
     *
     * @param userId   用户 ID
     * @param tenantId 租户 ID
     * @param username 用户名
     * @param issuedAt 签发时间（秒级时间戳）
     */
    private record GatewayAssertion(String userId, String tenantId, String username, long issuedAt) {
    }

    /**
     * 网关签名断言校验失败，统一以 401 对外响应。
     */
    private static final class GatewayAssertionRejectException extends Exception {

        private GatewayAssertionRejectException(String message) {
            super(message);
        }
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
