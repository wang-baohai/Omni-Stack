package com.omni.auth.security;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * {@link GatewayPreAuthFilter} 单元测试。
 */
class GatewayPreAuthFilterTest {

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


    /**
     * 内部接口应跳过网关预认证，业务接口仍应执行网关预认证。
     */
    @Test
    void should_skip_only_internal_api_paths() {
        GatewayPreAuthFilter filter = new GatewayPreAuthFilter();
        MockHttpServletRequest internalRequest =
                new MockHttpServletRequest("GET", "/internal/users/1");
        MockHttpServletRequest businessRequest =
                new MockHttpServletRequest("GET", "/user/list");

        assertThat(filter.shouldNotFilter(internalRequest)).isTrue();
        assertThat(filter.shouldNotFilter(businessRequest)).isFalse();
    }
}
