package com.omni.procurement.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.omni.common.core.result.BusinessException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import java.util.concurrent.atomic.AtomicBoolean;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** 采购请求身份与租户上下文过滤器测试。 */
class ProcTenantContextFilterTest {

    /** 每次测试后清理租户上下文。 */
    @AfterEach
    void clear() {
        ProcTenantContext.clear();
    }

    /** 缺少租户身份时必须返回 401 且不能进入业务链。 */
    @Test
    void shouldFailClosedWithoutTenantIdentity() throws Exception {
        ProcTenantContextFilter filter = new ProcTenantContextFilter(new ObjectMapper());
        MockHttpServletRequest request = new MockHttpServletRequest(
                "GET", "/api/procurement/material/list");
        request.addHeader("X-User-Id", "12");
        MockHttpServletResponse response = new MockHttpServletResponse();
        AtomicBoolean invoked = new AtomicBoolean();

        filter.doFilter(request, response, (req, res) -> invoked.set(true));

        assertThat(response.getStatus()).isEqualTo(401);
        assertThat(invoked).isFalse();
        assertThatThrownBy(ProcTenantContext::require).isInstanceOf(BusinessException.class);
    }

    /** 合法身份只在当前请求内可见，并在 finally 中清理。 */
    @Test
    void shouldBindAndClearTenantIdentity() throws Exception {
        ProcTenantContextFilter filter = new ProcTenantContextFilter(new ObjectMapper());
        MockHttpServletRequest request = new MockHttpServletRequest(
                "GET", "/api/procurement/material/list");
        request.addHeader("X-User-Id", "12");
        request.addHeader("X-Tenant-Id", "3");
        request.addHeader("X-User-Name", "buyer");
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, (req, res) -> {
            assertThat(ProcTenantContext.require().userId()).isEqualTo(12L);
            assertThat(ProcTenantContext.require().tenantId()).isEqualTo(3L);
            assertThat(ProcTenantContext.require().username()).isEqualTo("buyer");
        });

        assertThatThrownBy(ProcTenantContext::require).isInstanceOf(BusinessException.class);
    }

    /** 内部 API 由公共内部令牌过滤器处理，不应绑定外部用户上下文。 */
    @Test
    void shouldSkipInternalApi() throws Exception {
        ProcTenantContextFilter filter = new ProcTenantContextFilter(new ObjectMapper());
        MockHttpServletRequest request = new MockHttpServletRequest(
                "GET", "/api/internal/procurement/rfq/1/invitation");
        MockHttpServletResponse response = new MockHttpServletResponse();
        AtomicBoolean invoked = new AtomicBoolean();

        filter.doFilter(request, response, (req, res) -> invoked.set(true));

        assertThat(invoked).isTrue();
        assertThatThrownBy(ProcTenantContext::require).isInstanceOf(BusinessException.class);
    }
}
