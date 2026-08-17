package com.omni.procurement.security;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.concurrent.atomic.AtomicBoolean;

import static org.assertj.core.api.Assertions.assertThat;

/** 采购网关预认证过滤器测试。 */
class GatewayPreAuthFilterTest {

    /** 每次测试后清理认证上下文。 */
    @AfterEach
    void clear() {
        SecurityContextHolder.clearContext();
    }

    /** 未携带网关转发标记的业务请求必须拒绝。 */
    @Test
    void shouldRejectDirectBusinessRequest() throws Exception {
        GatewayPreAuthFilter filter = new GatewayPreAuthFilter();
        MockHttpServletRequest request = new MockHttpServletRequest(
                "GET", "/api/procurement/material/list");
        MockHttpServletResponse response = new MockHttpServletResponse();
        AtomicBoolean invoked = new AtomicBoolean();

        filter.doFilter(request, response, (req, res) -> invoked.set(true));

        assertThat(response.getStatus()).isEqualTo(403);
        assertThat(invoked).isFalse();
    }

    /** 合法网关身份应生成权限并在请求结束后清理。 */
    @Test
    void shouldBindAuthoritiesAndAlwaysClearSecurityContext() throws Exception {
        GatewayPreAuthFilter filter = new GatewayPreAuthFilter();
        MockHttpServletRequest request = new MockHttpServletRequest(
                "GET", "/api/procurement/material/list");
        request.addHeader("X-Gateway-Forwarded", "true");
        request.addHeader("X-User-Id", "12");
        request.addHeader("X-User-Name", "buyer");
        request.addHeader("X-User-Roles", "PROCUREMENT_STAFF");
        request.addHeader("X-User-Scopes",
                "procurement:material:list procurement:requisition:create");
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, (req, res) -> {
            assertThat(SecurityContextHolder.getContext().getAuthentication().getName())
                    .isEqualTo("buyer");
            assertThat(SecurityContextHolder.getContext().getAuthentication().getAuthorities())
                    .extracting("authority")
                    .containsExactlyInAnyOrder(
                            "ROLE_PROCUREMENT_STAFF",
                            "procurement:material:list",
                            "procurement:requisition:create");
        });

        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
    }

    /** 内部 API 不应依赖会被外层过滤器清理的 Spring Security 身份。 */
    @Test
    void shouldSkipInternalApi() throws Exception {
        GatewayPreAuthFilter filter = new GatewayPreAuthFilter();
        MockHttpServletRequest request = new MockHttpServletRequest(
                "GET", "/api/internal/procurement/rfq/invitations");
        MockHttpServletResponse response = new MockHttpServletResponse();
        AtomicBoolean invoked = new AtomicBoolean();

        filter.doFilter(request, response, (req, res) -> invoked.set(true));

        assertThat(invoked).isTrue();
    }
}
