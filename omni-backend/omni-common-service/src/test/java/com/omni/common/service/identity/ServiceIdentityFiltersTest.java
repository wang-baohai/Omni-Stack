package com.omni.common.service.identity;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.omni.common.service.config.ServiceIdentityProperties;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.context.SecurityContextHolder;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.concurrent.atomic.AtomicBoolean;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

import static org.assertj.core.api.Assertions.assertThat;

class ServiceIdentityFiltersTest {

    private final ServiceIdentityProperties properties = properties();
    private final ServicePathPolicy pathPolicy = new ServicePathPolicy(properties);
    private final ObjectMapper objectMapper = new ObjectMapper();

    /** 未配置密钥时，伪造断言不能将明文身份头升级为可信身份。 */
    @Test
    void shouldRejectForgedAssertionWithoutSecret() throws Exception {
        GatewayPreAuthenticationFilter filter = new GatewayPreAuthenticationFilter(
                properties, pathPolicy, objectMapper);
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/crm/leads");
        request.addHeader("X-Gateway-Forwarded", "true");
        request.addHeader("X-Gateway-Assertion", "forged");
        request.addHeader("X-User-Id", "7");
        request.addHeader("X-Tenant-Id", "1");
        request.addHeader("X-User-Roles", "SUPER_ADMIN");
        MockHttpServletResponse response = new MockHttpServletResponse();
        AtomicBoolean invoked = new AtomicBoolean();

        filter.doFilter(request, response, (req, res) -> invoked.set(true));

        assertThat(invoked).isFalse();
        assertThat(response.getStatus()).isEqualTo(401);
    }


    @AfterEach
    void clearContexts() {
        SecurityContextHolder.clearContext();
        ServiceIdentityContext.clear();
    }

    @Test
    void shouldRejectDirectBusinessAccess() throws Exception {
        GatewayPreAuthenticationFilter filter = new GatewayPreAuthenticationFilter(
                properties, pathPolicy, objectMapper);
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/crm/leads");
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, (req, res) -> { });

        assertThat(response.getStatus()).isEqualTo(403);
        assertThat(response.getContentAsString()).contains("禁止直接访问 CRM 服务");
    }

    /** D01 修复后：密钥未配置时明文身份头被 fail-closed 拒绝（401）。 */
    @Test
    void shouldRejectPlaintextHeadersWhenNoSecretConfigured() throws Exception {
        GatewayPreAuthenticationFilter filter = new GatewayPreAuthenticationFilter(
                properties, pathPolicy, objectMapper);
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/crm/leads");
        request.addHeader("X-Gateway-Forwarded", "true");
        request.addHeader("X-User-Id", "7");
        request.addHeader("X-User-Name", "alice");
        request.addHeader("X-User-Roles", "USER,MANAGER");
        request.addHeader("X-User-Scopes", "crm:lead:list");
        MockHttpServletResponse response = new MockHttpServletResponse();
        AtomicBoolean invoked = new AtomicBoolean();

        filter.doFilter(request, response, (req, res) -> invoked.set(true));

        assertThat(invoked).isFalse();
        assertThat(response.getStatus()).isEqualTo(401);
        assertThat(response.getContentAsString()).contains("缺少网关签名断言");
        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
    }

    /** D01 修复后：密钥已配置且签名断言有效时正常构建 Authentication。 */
    @Test
    void shouldBuildAuthenticationWithValidAssertionAndClearAfterRequest() throws Exception {
        String secret = "unit-test-gateway-assertion-secret-at-least-32-bytes-long";
        GatewayPreAuthenticationFilter filter = new GatewayPreAuthenticationFilter(
                properties, pathPolicy, objectMapper);
        filter.setAssertionSecret(secret);
        long now = System.currentTimeMillis() / 1000L;
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/crm/leads");
        request.addHeader("X-Gateway-Forwarded", "true");
        request.addHeader("X-User-Id", "7");
        request.addHeader("X-User-Name", "alice");
        request.addHeader("X-User-Roles", "USER,MANAGER");
        request.addHeader("X-User-Scopes", "crm:lead:list");
        request.addHeader("X-Gateway-Assertion", buildAssertion("7", "1", "alice", now, secret));
        MockHttpServletResponse response = new MockHttpServletResponse();
        AtomicBoolean invoked = new AtomicBoolean();

        filter.doFilter(request, response, (req, res) -> {
            invoked.set(true);
            assertThat(SecurityContextHolder.getContext().getAuthentication().getName()).isEqualTo("alice");
            assertThat(SecurityContextHolder.getContext().getAuthentication().getAuthorities())
                    .extracting("authority")
                    .contains("ROLE_USER", "ROLE_MANAGER", "crm:lead:list");
        });

        assertThat(invoked).isTrue();
        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
    }

    private static String buildAssertion(String userId, String tenantId, String username,
                                         long issuedAt, String secret) throws Exception {
        String payload = userId + "|" + tenantId + "|" + username + "|" + issuedAt;
        Base64.Encoder encoder = Base64.getUrlEncoder().withoutPadding();
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
        return encoder.encodeToString(payload.getBytes(StandardCharsets.UTF_8))
                + ":" + encoder.encodeToString(mac.doFinal(payload.getBytes(StandardCharsets.UTF_8)));
    }

    @Test
    void shouldFailClosedWithoutPositiveUserAndTenant() throws Exception {
        ServiceIdentityFilter filter = new ServiceIdentityFilter(pathPolicy, objectMapper);
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/crm/leads");
        request.addHeader("X-User-Id", "0");
        request.addHeader("X-Tenant-Id", "1");
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, (req, res) -> { });

        assertThat(response.getStatus()).isEqualTo(401);
        assertThat(response.getContentAsString()).contains("缺少合法的用户或租户身份");
    }

    @Test
    void shouldBindImmutableIdentityAndClearItAfterRequest() throws Exception {
        ServiceIdentityFilter filter = new ServiceIdentityFilter(pathPolicy, objectMapper);
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/crm/leads");
        request.addHeader("X-User-Id", "7");
        request.addHeader("X-Tenant-Id", "3");
        request.addHeader("X-User-Name", "alice");
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, (req, res) -> {
            ServiceRequestIdentity identity = ServiceIdentityContext.require();
            assertThat(identity).isEqualTo(new ServiceRequestIdentity(7L, 3L, "alice"));
        });

        assertThatThrownByContextRequirement();
    }

    private void assertThatThrownByContextRequirement() {
        org.assertj.core.api.Assertions.assertThatThrownBy(ServiceIdentityContext::require)
                .hasMessageContaining("缺少服务请求身份上下文");
    }

    private ServiceIdentityProperties properties() {
        ServiceIdentityProperties result = new ServiceIdentityProperties();
        result.setName("omni-crm");
        result.setDisplayName("CRM");
        return result;
    }
}
