package com.omni.common.service.identity;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.omni.common.core.result.R;
import com.omni.common.service.config.ServiceIdentityProperties;
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
import java.security.InvalidKeyException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Base64;
import java.util.List;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

/**
 * 将可信 Gateway 身份头转换为 Spring Security 认证对象。
 * <p>
 * 明文身份头无法阻止“直连服务端口伪造请求头”的绕过，因此本过滤器优先验证
 * {@code X-Gateway-Assertion}（Gateway 用共享密钥对 {@code userId|tenantId|username|issuedAt}
 * 签发的 HMAC-SHA256 断言，时效±5 分钟）：
 * </p>
 * <ul>
 *   <li>断言签名有效且与明文身份头一致 → 以断言身份构建 {@code Authentication}</li>
 *   <li>断言签名无效 / 过期 / 与身份头不一致 → 401 拒绝</li>
 *   <li>密钥已配置但断言缺失而携带 {@code X-User-Id} → 401 拒绝</li>
 *   <li>密钥未配置 → 同样 401 拒绝（fail-closed），不再信任明文身份头</li>
 * </ul>
 *
 * @author Omni-Stack Team
 */
@Slf4j
@RequiredArgsConstructor
public class GatewayPreAuthenticationFilter extends OncePerRequestFilter {

    /** Gateway 签名断言头，防伪造身份的信任凭证。 */
    private static final String HEADER_GATEWAY_ASSERTION = "X-Gateway-Assertion";

    /** 断言头格式：{@code Base64(payload) + ":" + Base64(HMAC)}。 */
    private static final String ASSERTION_SEPARATOR = ":";

    /** 断言 payload 分隔正则。 */
    private static final String ASSERTION_PAYLOAD_SPLIT = "\\|";

    /** 断言签名算法。 */
    private static final String ASSERTION_HMAC_ALGORITHM = "HmacSHA256";

    /** 密钥环境变量回退名（方便容器直接注入，无需改 application.yml）。 */
    private static final String ASSERTION_SECRET_ENV = "GATEWAY_ASSERTION_SECRET";

    /** 断言时间戳允许偏差（秒），超出视为过期。 */
    private static final long ASSERTION_TTL_SECONDS = 300L;

    /** HMAC 密钥推荐最小长度（字节），低于此强度仅告警不拒绝，由网关侧 fail-closed 保证。 */
    private static final int MIN_ASSERTION_SECRET_BYTES = 32;

    private static final String ASSERTION_INVALID_MESSAGE = "网关签名断言无效";
    private static final String ASSERTION_EXPIRED_MESSAGE = "网关签名断言过期，请重试";
    private static final String ASSERTION_MISSING_MESSAGE = "缺少网关签名断言，请通过 Gateway 访问";

    private final ServiceIdentityProperties properties;
    private final ServicePathPolicy pathPolicy;
    private final ObjectMapper objectMapper;

    /**
     * 网关签名断言密钥。由自动配置通过 {@link #setAssertionSecret(String)} 传入；
     * 未传入时回退环境变量 {@code GATEWAY_ASSERTION_SECRET}。声明为非 final 字段以免改变
     * {@code @RequiredArgsConstructor} 己有构造参数顺序。
     */
    private String assertionSecret;

    /**
     * 设置网关签名断言密钥。
     *
     * @param assertionSecret 共享密钥，为空表示不启用断言校验
     */
    public void setAssertionSecret(String assertionSecret) {
        this.assertionSecret = assertionSecret;
    }

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

        String assertion = request.getHeader(HEADER_GATEWAY_ASSERTION);
        String secret = resolveAssertionSecret();
        String userId = request.getHeader("X-User-Id");
        String username = request.getHeader("X-User-Name");
        String tenantId = request.getHeader("X-Tenant-Id");
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
                // 断言与明文头不一致时直接拒绝：同启动的 ServiceIdentityFilter 等组件仍会直读明文头
                if (!matches(verified.userId(), userId) || !matches(verified.tenantId(), tenantId)
                        || !matches(verified.username(), username)) {
                    log.warn("网关签名断言与明文身份头不一致，拒绝信任: URI={}", uri);
                    writeUnauthorized(response, ASSERTION_INVALID_MESSAGE);
                    return;
                }
                userId = verified.userId();
                tenantId = verified.tenantId();
                username = verified.username();
                log.debug("网关签名断言校验通过：user={}, tenant={}, issuedAt={}",
                        userId, tenantId, verified.issuedAt());
            }
        } else if (identityPresent) {
            // D01 修复：移除过渡模式回退，密钥未配置时也拒绝信任明文身份头（fail-closed）
            log.warn("身份头缺少网关签名断言或断言密钥未配置，拒绝信任: URI={}, X-User-Id={}", uri, userId);
            writeUnauthorized(response, ASSERTION_MISSING_MESSAGE);
            return;
        }

        if (userId == null || userId.isBlank()) {
            filterChain.doFilter(request, response);
            return;
        }
        List<SimpleGrantedAuthority> authorities = new ArrayList<>(16);
        addAuthorities(authorities, request.getHeader("X-User-Roles"), true);
        addAuthorities(authorities, request.getHeader("X-User-Scopes"), false);
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
        String assertedUsername = String.join("|", Arrays.copyOfRange(fields, 2, fields.length - 1)).trim();
        return new GatewayAssertion(assertedUserId, assertedTenantId, assertedUsername, issuedAt);
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
     * 写回 401 断言校验失败响应。
     *
     * @param response HTTP 响应
     * @param message  错误消息
     * @throws IOException 写出失败
     */
    private void writeUnauthorized(HttpServletResponse response, String message) throws IOException {
        response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding("UTF-8");
        objectMapper.writeValue(response.getWriter(), R.fail(401, message));
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
}
