package com.omni.base.security;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * {@link GatewayPreAuthFilter} 网关签名断言（HMAC）测试。
 * <p>
 * 覆盖漏洞场景：攻击者直连服务端口伪造 {@code X-Gateway-Forwarded} / {@code X-User-Id}
 * 绕过认证。断言启用后必须拒绝此类请求。
 * </p>
 */
class GatewayPreAuthFilterHmacTest {

    /** 未配置密钥时，伪造断言不能将明文身份头升级为可信身份。 */
    @Test
    void shouldRejectForgedAssertionWithoutSecret() throws Exception {
        GatewayPreAuthFilter filter = new GatewayPreAuthFilter();
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/business/list");
        request.addHeader("X-Gateway-Forwarded", "true");
        request.addHeader("X-Gateway-Assertion", "forged");
        request.addHeader("X-User-Id", "7");
        request.addHeader("X-Tenant-Id", "1");
        request.addHeader("X-User-Roles", "SUPER_ADMIN");
        org.springframework.mock.web.MockHttpServletResponse response =
                new org.springframework.mock.web.MockHttpServletResponse();
        java.util.concurrent.atomic.AtomicBoolean invoked = new java.util.concurrent.atomic.AtomicBoolean();

        filter.doFilter(request, response, (req, res) -> invoked.set(true));

        org.junit.jupiter.api.Assertions.assertFalse(invoked.get());
        org.junit.jupiter.api.Assertions.assertEquals(401, response.getStatus());
    }


    private static final String SECRET = "unit-test-gateway-assertion-secret";
    private static final String BUSINESS_URI = "/api/base/dict/types";

    @AfterEach
    void clearContext() {
        SecurityContextHolder.clearContext();
    }

    /** 测试1：既无签名断言也无转发标记时保持原有 403 直连拒绝。 */
    @Test
    void shouldRejectDirectAccessWithoutAssertionAndForwarded() throws Exception {
        GatewayPreAuthFilter filter = filterWithSecret();
        MockHttpServletRequest request = new MockHttpServletRequest("GET", BUSINESS_URI);
        request.addHeader("X-User-Id", "7");
        request.addHeader("X-Tenant-Id", "1");
        request.addHeader("X-User-Name", "alice");
        MockHttpServletResponse response = new MockHttpServletResponse();
        AtomicBoolean invoked = new AtomicBoolean();

        filter.doFilter(request, response, (req, res) -> invoked.set(true));

        assertThat(invoked).isFalse();
        assertThat(response.getStatus()).isEqualTo(403);
        assertThat(response.getContentAsString()).contains("禁止直接访问后端服务");
    }

    /** 测试2：有 X-Gateway-Forwarded 但缺签名断言且密钥已配置时返回 401（伪造头绕过被封堵）。 */
    @Test
    void shouldRejectSpoofedIdentityHeadersWhenSecretConfigured() throws Exception {
        GatewayPreAuthFilter filter = filterWithSecret();
        MockHttpServletRequest request = new MockHttpServletRequest("GET", BUSINESS_URI);
        request.addHeader("X-Gateway-Forwarded", "true");
        request.addHeader("X-User-Id", "7");
        request.addHeader("X-Tenant-Id", "1");
        request.addHeader("X-User-Name", "attacker");
        request.addHeader("X-User-Roles", "SUPER_ADMIN");
        MockHttpServletResponse response = new MockHttpServletResponse();
        AtomicBoolean invoked = new AtomicBoolean();

        filter.doFilter(request, response, (req, res) -> invoked.set(true));

        assertThat(invoked).isFalse();
        assertThat(response.getStatus()).isEqualTo(401);
        assertThat(response.getContentAsString()).contains("\"code\":401");
        assertThat(response.getContentAsString()).contains("缺少网关签名断言");
        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
    }

    /** 测试3：签名断言有效时以断言身份构建 Authentication 并放行。 */
    @Test
    void shouldAuthenticateWithValidAssertion() throws Exception {
        GatewayPreAuthFilter filter = filterWithSecret();
        long now = System.currentTimeMillis() / 1000L;
        MockHttpServletRequest request = new MockHttpServletRequest("GET", BUSINESS_URI);
        request.addHeader("X-Gateway-Forwarded", "true");
        request.addHeader("X-User-Id", "7");
        request.addHeader("X-Tenant-Id", "1");
        request.addHeader("X-User-Name", "alice");
        request.addHeader("X-User-Roles", "USER,MANAGER");
        request.addHeader("X-User-Scopes", "dict:type:list");
        request.addHeader("X-Gateway-Assertion", assertion("7", "1", "alice", now, SECRET));
        MockHttpServletResponse response = new MockHttpServletResponse();
        AtomicBoolean invoked = new AtomicBoolean();

        filter.doFilter(request, response, (req, res) -> {
            invoked.set(true);
            Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
            assertThat(authentication).isNotNull();
            assertThat(authentication.getName()).isEqualTo("alice");
            assertThat(authentication.getAuthorities())
                    .extracting("authority")
                    .contains("ROLE_USER", "ROLE_MANAGER", "dict:type:list");
        });

        assertThat(invoked).isTrue();
        assertThat(response.getStatus()).isEqualTo(200);
    }

    /** 测试4：断言签名被篡改时返回 401「网关签名断言无效」。 */
    @Test
    void shouldRejectTamperedAssertionSignature() throws Exception {
        GatewayPreAuthFilter filter = filterWithSecret();
        long now = System.currentTimeMillis() / 1000L;
        MockHttpServletRequest request = new MockHttpServletRequest("GET", BUSINESS_URI);
        request.addHeader("X-Gateway-Forwarded", "true");
        request.addHeader("X-User-Id", "7");
        request.addHeader("X-Tenant-Id", "1");
        request.addHeader("X-User-Name", "alice");
        // 沿用合法签名段，但把 payload 换成提权身份，造成签名与内容不匹配
        String valid = assertion("7", "1", "alice", now, SECRET);
        String tamperedPayloadPart = Base64.getUrlEncoder().withoutPadding()
                .encodeToString(("8|1|attacker|" + now).getBytes(StandardCharsets.UTF_8));
        request.addHeader("X-Gateway-Assertion",
                tamperedPayloadPart + valid.substring(valid.indexOf(':')));
        MockHttpServletResponse response = new MockHttpServletResponse();
        AtomicBoolean invoked = new AtomicBoolean();

        filter.doFilter(request, response, (req, res) -> invoked.set(true));

        assertThat(invoked).isFalse();
        assertThat(response.getStatus()).isEqualTo(401);
        assertThat(response.getContentAsString()).contains("网关签名断言无效");
    }

    /** 测试5：断言时间戳超过 5 分钟窗口时返回 401「网关签名断言过期，请重试」。 */
    @Test
    void shouldRejectExpiredAssertion() throws Exception {
        GatewayPreAuthFilter filter = filterWithSecret();
        long expired = System.currentTimeMillis() / 1000L - 400L;
        MockHttpServletRequest request = new MockHttpServletRequest("GET", BUSINESS_URI);
        request.addHeader("X-Gateway-Forwarded", "true");
        request.addHeader("X-User-Id", "7");
        request.addHeader("X-Tenant-Id", "1");
        request.addHeader("X-User-Name", "alice");
        request.addHeader("X-Gateway-Assertion", assertion("7", "1", "alice", expired, SECRET));
        MockHttpServletResponse response = new MockHttpServletResponse();
        AtomicBoolean invoked = new AtomicBoolean();

        filter.doFilter(request, response, (req, res) -> invoked.set(true));

        assertThat(invoked).isFalse();
        assertThat(response.getStatus()).isEqualTo(401);
        assertThat(response.getContentAsString()).contains("网关签名断言过期，请重试");
    }

    /** 测试6：断言身份与明文身份头不一致时拒绝，避免下游组件继续直读被篡改的头。 */
    @Test
    void shouldRejectAssertionThatDisagreesWithPlainHeaders() throws Exception {
        GatewayPreAuthFilter filter = filterWithSecret();
        long now = System.currentTimeMillis() / 1000L;
        MockHttpServletRequest request = new MockHttpServletRequest("GET", BUSINESS_URI);
        request.addHeader("X-Gateway-Forwarded", "true");
        request.addHeader("X-User-Id", "999");
        request.addHeader("X-Tenant-Id", "1");
        request.addHeader("X-User-Name", "alice");
        request.addHeader("X-Gateway-Assertion", assertion("7", "1", "alice", now, SECRET));
        MockHttpServletResponse response = new MockHttpServletResponse();
        AtomicBoolean invoked = new AtomicBoolean();

        filter.doFilter(request, response, (req, res) -> invoked.set(true));

        assertThat(invoked).isFalse();
        assertThat(response.getStatus()).isEqualTo(401);
        assertThat(response.getContentAsString()).contains("网关签名断言无效");
    }

    /** 测试7：使用另一密钥签发的断言必须被拒绝。 */
    @Test
    void shouldRejectAssertionSignedWithDifferentSecret() throws Exception {
        GatewayPreAuthFilter filter = filterWithSecret();
        long now = System.currentTimeMillis() / 1000L;
        MockHttpServletRequest request = new MockHttpServletRequest("GET", BUSINESS_URI);
        request.addHeader("X-Gateway-Forwarded", "true");
        request.addHeader("X-User-Id", "7");
        request.addHeader("X-Tenant-Id", "1");
        request.addHeader("X-User-Name", "alice");
        request.addHeader("X-Gateway-Assertion",
                assertion("7", "1", "alice", now, "another-secret"));
        MockHttpServletResponse response = new MockHttpServletResponse();
        AtomicBoolean invoked = new AtomicBoolean();

        filter.doFilter(request, response, (req, res) -> invoked.set(true));

        assertThat(invoked).isFalse();
        assertThat(response.getStatus()).isEqualTo(401);
        assertThat(response.getContentAsString()).contains("网关签名断言无效");
    }

    /** 测试8：标准 Base64（含填充）字母表的断言同样可被解析，保证编码兼容。 */
    @Test
    void shouldAcceptStandardBase64AlphabetAssertion() throws Exception {
        GatewayPreAuthFilter filter = filterWithSecret();
        long now = System.currentTimeMillis() / 1000L;
        String payload = "7|1|alice|" + now;
        String assertion = Base64.getEncoder().encodeToString(payload.getBytes(StandardCharsets.UTF_8))
                + ":" + Base64.getEncoder().encodeToString(hmac(payload, SECRET));
        MockHttpServletRequest request = new MockHttpServletRequest("GET", BUSINESS_URI);
        request.addHeader("X-Gateway-Forwarded", "true");
        request.addHeader("X-User-Id", "7");
        request.addHeader("X-Tenant-Id", "1");
        request.addHeader("X-User-Name", "alice");
        request.addHeader("X-Gateway-Assertion", assertion);
        MockHttpServletResponse response = new MockHttpServletResponse();
        AtomicBoolean invoked = new AtomicBoolean();

        filter.doFilter(request, response, (req, res) -> invoked.set(true));

        assertThat(invoked).isTrue();
    }

    /** 测试9：D01 修复后密钥未配置时也 fail-closed，不再信任明文身份头。 */
    @Test
    void shouldRejectPlaintextHeadersWhenNoSecretConfigured() throws Exception {
        GatewayPreAuthFilter filter = new GatewayPreAuthFilter();
        MockHttpServletRequest request = new MockHttpServletRequest("GET", BUSINESS_URI);
        request.addHeader("X-Gateway-Forwarded", "true");
        request.addHeader("X-User-Id", "7");
        request.addHeader("X-Tenant-Id", "1");
        request.addHeader("X-User-Name", "alice");
        MockHttpServletResponse response = new MockHttpServletResponse();
        AtomicBoolean invoked = new AtomicBoolean();

        filter.doFilter(request, response, (req, res) -> invoked.set(true));

        assertThat(invoked).isFalse();
        assertThat(response.getStatus()).isEqualTo(401);
        assertThat(response.getContentAsString()).contains("缺少网关签名断言");
        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
    }

    /** 测试10：内部 API 由共享密钥过滤器负责，不要求网关断言。 */
    @Test
    void shouldNotFilterInternalApiPaths() {
        GatewayPreAuthFilter filter = new GatewayPreAuthFilter();
        MockHttpServletRequest internalRequest =
                new MockHttpServletRequest("GET", "/api/internal/mq-message/pending");
        MockHttpServletRequest businessRequest =
                new MockHttpServletRequest("GET", BUSINESS_URI);

        assertThat(filter.shouldNotFilter(internalRequest)).isTrue();
        assertThat(filter.shouldNotFilter(businessRequest)).isFalse();
    }

    /** 测试11：无身份头的网关公开路径请求（如登录）不应因缺少断言被拒绝。 */
    @Test
    void shouldAllowGatewayPublicPathWithoutIdentityHeaders() throws Exception {
        GatewayPreAuthFilter filter = filterWithSecret();
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/base/public/config");
        request.addHeader("X-Gateway-Forwarded", "true");
        MockHttpServletResponse response = new MockHttpServletResponse();
        AtomicBoolean invoked = new AtomicBoolean();

        filter.doFilter(request, response, (req, res) -> invoked.set(true));

        assertThat(invoked).isTrue();
        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
    }

    private GatewayPreAuthFilter filterWithSecret() {
        GatewayPreAuthFilter filter = new GatewayPreAuthFilter();
        filter.setAssertionSecret(SECRET);
        return filter;
    }

    /**
     * 构造与 Gateway AuthFilter 一致的签名断言：{@code Base64URL(payload):Base64URL(HMAC)}。
     */
    private static String assertion(String userId, String tenantId, String username,
                                    long issuedAt, String secret) throws Exception {
        String payload = userId + "|" + tenantId + "|" + username + "|" + issuedAt;
        Base64.Encoder encoder = Base64.getUrlEncoder().withoutPadding();
        return encoder.encodeToString(payload.getBytes(StandardCharsets.UTF_8))
                + ":" + encoder.encodeToString(hmac(payload, secret));
    }

    private static byte[] hmac(String payload, String secret) throws Exception {
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
        return mac.doFinal(payload.getBytes(StandardCharsets.UTF_8));
    }
}
