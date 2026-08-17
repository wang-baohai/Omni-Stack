package com.omni.asset.security;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.concurrent.atomic.AtomicBoolean;

import static org.assertj.core.api.Assertions.assertThat;

/** 资产网关预认证过滤器测试。 */
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
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/asset/asset/list");
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
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/asset/asset/list");
        request.addHeader("X-Gateway-Forwarded", "true");
        request.addHeader("X-User-Id", "12");
        request.addHeader("X-User-Name", "asset-user");
        request.addHeader("X-User-Roles", "ASSET_USER");
        request.addHeader("X-User-Scopes", "asset:asset:self asset:asset:return");
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, (req, res) -> {
            assertThat(SecurityContextHolder.getContext().getAuthentication().getName())
                    .isEqualTo("asset-user");
            assertThat(SecurityContextHolder.getContext().getAuthentication().getAuthorities())
                    .extracting("authority")
                    .containsExactlyInAnyOrder(
                            "ROLE_ASSET_USER", "asset:asset:self", "asset:asset:return");
        });

        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
    }

    /** 内部 API 不应依赖外部用户预认证。 */
    @Test
    void shouldSkipInternalApi() throws Exception {
        GatewayPreAuthFilter filter = new GatewayPreAuthFilter();
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/internal/asset/search");
        MockHttpServletResponse response = new MockHttpServletResponse();
        AtomicBoolean invoked = new AtomicBoolean();

        filter.doFilter(request, response, (req, res) -> invoked.set(true));

        assertThat(invoked).isTrue();
    }
}
