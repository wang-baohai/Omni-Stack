package com.omni.auth.security;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * {@link GatewayPreAuthFilter} 单元测试。
 */
class GatewayPreAuthFilterTest {

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
