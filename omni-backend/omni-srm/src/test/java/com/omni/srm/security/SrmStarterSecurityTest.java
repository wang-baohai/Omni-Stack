package com.omni.srm.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.omni.common.core.result.BusinessException;
import com.omni.common.service.config.ServiceIdentityProperties;
import com.omni.common.service.identity.GatewayPreAuthenticationFilter;
import com.omni.common.service.identity.ServiceIdentityContext;
import com.omni.common.service.identity.ServiceIdentityFilter;
import com.omni.common.service.identity.ServicePathPolicy;
import jakarta.servlet.FilterChain;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.concurrent.atomic.AtomicBoolean;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** SRM 采用公共 Starter 后的预认证与身份失败关闭回归测试。 */
class SrmStarterSecurityTest {

    /** 每次测试后清理共享线程上下文。 */
    @AfterEach
    void clear() {
        ServiceIdentityContext.clear();
        SecurityContextHolder.clearContext();
    }

    /** 未携带网关转发标记的 SRM 业务请求必须拒绝。 */
    @Test
    void shouldRejectDirectBusinessRequest() throws Exception {
        GatewayPreAuthenticationFilter filter = gatewayFilter();
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/srm/supplier/list");
        MockHttpServletResponse response = new MockHttpServletResponse();
        AtomicBoolean invoked = new AtomicBoolean();

        filter.doFilter(request, response, (req, res) -> invoked.set(true));

        assertThat(response.getStatus()).isEqualTo(403);
        assertThat(response.getContentAsString()).contains("SRM");
        assertThat(invoked).isFalse();
    }

    /** 合法网关身份应生成权限并在请求结束后清理认证。 */
    @Test
    void shouldBindAuthoritiesAndClearSecurityContext() throws Exception {
        GatewayPreAuthenticationFilter filter = gatewayFilter();
        MockHttpServletRequest request = businessRequest();
        request.addHeader("X-User-Roles", "PURCHASE_MANAGER");
        request.addHeader("X-User-Scopes", "srm:supplier:list srm:evaluation:create");
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, (req, res) -> {
            assertThat(SecurityContextHolder.getContext().getAuthentication().getName()).isEqualTo("buyer");
            assertThat(SecurityContextHolder.getContext().getAuthentication().getAuthorities())
                    .extracting("authority")
                    .containsExactlyInAnyOrder(
                            "ROLE_PURCHASE_MANAGER", "srm:supplier:list", "srm:evaluation:create");
        });

        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
    }

    /** 缺少租户身份时必须返回 401 且不进入业务链。 */
    @Test
    void shouldFailClosedWithoutTenantIdentity() throws Exception {
        ServiceIdentityFilter filter = identityFilter();
        MockHttpServletRequest request = businessRequest();
        request.removeHeader("X-Tenant-Id");
        MockHttpServletResponse response = new MockHttpServletResponse();
        AtomicBoolean invoked = new AtomicBoolean();

        filter.doFilter(request, response, (req, res) -> invoked.set(true));

        assertThat(response.getStatus()).isEqualTo(401);
        assertThat(invoked).isFalse();
        assertThatThrownBy(ServiceIdentityContext::require).isInstanceOf(BusinessException.class);
    }

    /** 合法身份只在 SRM 请求内可见，并在 finally 中清理。 */
    @Test
    void shouldBindAndClearRequestIdentity() throws Exception {
        ServiceIdentityFilter filter = identityFilter();
        MockHttpServletRequest request = businessRequest();
        MockHttpServletResponse response = new MockHttpServletResponse();

        FilterChain chain = (req, res) -> {
            assertThat(ServiceIdentityContext.require().userId()).isEqualTo(21L);
            assertThat(ServiceIdentityContext.require().tenantId()).isEqualTo(6L);
        };
        filter.doFilter(request, response, chain);

        assertThatThrownBy(ServiceIdentityContext::require).isInstanceOf(BusinessException.class);
    }

    private GatewayPreAuthenticationFilter gatewayFilter() {
        ServiceIdentityProperties properties = properties();
        return new GatewayPreAuthenticationFilter(
                properties, new ServicePathPolicy(properties), new ObjectMapper());
    }

    private ServiceIdentityFilter identityFilter() {
        ServiceIdentityProperties properties = properties();
        return new ServiceIdentityFilter(new ServicePathPolicy(properties), new ObjectMapper());
    }

    private ServiceIdentityProperties properties() {
        ServiceIdentityProperties properties = new ServiceIdentityProperties();
        properties.setName("omni-srm");
        properties.setDisplayName("SRM");
        return properties;
    }

    private MockHttpServletRequest businessRequest() {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/srm/supplier/list");
        request.addHeader("X-Gateway-Forwarded", "true");
        request.addHeader("X-User-Id", "21");
        request.addHeader("X-Tenant-Id", "6");
        request.addHeader("X-User-Name", "buyer");
        return request;
    }
}
