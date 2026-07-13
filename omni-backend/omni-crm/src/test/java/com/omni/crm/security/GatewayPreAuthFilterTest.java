package com.omni.crm.security;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.concurrent.atomic.AtomicBoolean;

import static org.assertj.core.api.Assertions.assertThat;

/** CRM 网关预认证过滤器测试。 */
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
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/crm/lead/list");
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
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/crm/lead/list");
        request.addHeader("X-Gateway-Forwarded", "true");
        request.addHeader("X-User-Id", "12");
        request.addHeader("X-User-Name", "sales");
        request.addHeader("X-User-Roles", "SALES_REP");
        request.addHeader("X-User-Scopes", "crm:lead:list crm:activity:create");
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, (req, res) -> {
            assertThat(SecurityContextHolder.getContext().getAuthentication().getName()).isEqualTo("sales");
            assertThat(SecurityContextHolder.getContext().getAuthentication().getAuthorities())
                    .extracting("authority")
                    .containsExactlyInAnyOrder("ROLE_SALES_REP", "crm:lead:list", "crm:activity:create");
        });

        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
    }
}
