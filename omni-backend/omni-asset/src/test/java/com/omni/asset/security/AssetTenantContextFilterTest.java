package com.omni.asset.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.omni.common.core.result.BusinessException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import java.util.concurrent.atomic.AtomicBoolean;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** 资产请求身份与租户上下文过滤器测试。 */
class AssetTenantContextFilterTest {

    /** 每次测试后清理租户上下文。 */
    @AfterEach
    void clear() {
        AssetTenantContext.clear();
    }

    /** 缺少租户身份时必须返回 401 且不能进入业务链。 */
    @Test
    void shouldFailClosedWithoutTenantIdentity() throws Exception {
        AssetTenantContextFilter filter = new AssetTenantContextFilter(new ObjectMapper());
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/asset/asset/list");
        request.addHeader("X-User-Id", "12");
        MockHttpServletResponse response = new MockHttpServletResponse();
        AtomicBoolean invoked = new AtomicBoolean();

        filter.doFilter(request, response, (req, res) -> invoked.set(true));

        assertThat(response.getStatus()).isEqualTo(401);
        assertThat(invoked).isFalse();
        assertThatThrownBy(AssetTenantContext::require).isInstanceOf(BusinessException.class);
    }

    /** 合法身份只在当前请求内可见，并在 finally 中清理。 */
    @Test
    void shouldBindAndClearTenantIdentity() throws Exception {
        AssetTenantContextFilter filter = new AssetTenantContextFilter(new ObjectMapper());
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/asset/asset/list");
        request.addHeader("X-User-Id", "12");
        request.addHeader("X-Tenant-Id", "3");
        request.addHeader("X-User-Name", "asset-user");
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, (req, res) -> {
            assertThat(AssetTenantContext.require().userId()).isEqualTo(12L);
            assertThat(AssetTenantContext.require().tenantId()).isEqualTo(3L);
            assertThat(AssetTenantContext.require().username()).isEqualTo("asset-user");
        });

        assertThatThrownBy(AssetTenantContext::require).isInstanceOf(BusinessException.class);
    }

    /** 内部 API 由公共内部令牌过滤器处理，不绑定外部用户上下文。 */
    @Test
    void shouldSkipInternalApi() throws Exception {
        AssetTenantContextFilter filter = new AssetTenantContextFilter(new ObjectMapper());
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/internal/asset/search");
        MockHttpServletResponse response = new MockHttpServletResponse();
        AtomicBoolean invoked = new AtomicBoolean();

        filter.doFilter(request, response, (req, res) -> invoked.set(true));

        assertThat(invoked).isTrue();
        assertThatThrownBy(AssetTenantContext::require).isInstanceOf(BusinessException.class);
    }
}
