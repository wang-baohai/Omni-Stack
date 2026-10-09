package com.omni.workflow.security;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Gateway 预认证过滤器路径边界测试。
 *
 * @author Omni-Stack Team
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


    private final GatewayPreAuthFilter filter = new GatewayPreAuthFilter();

    @Test
    void shouldSkipAllInternalApiPaths() {
        MockHttpServletRequest workflowRequest = new MockHttpServletRequest(
                "POST", "/api/internal/workflow/process-instance/start");
        MockHttpServletRequest mqRequest = new MockHttpServletRequest(
                "GET", "/api/internal/mq-message/query");

        assertTrue(filter.shouldNotFilter(workflowRequest));
        assertTrue(filter.shouldNotFilter(mqRequest));
    }

    @Test
    void shouldKeepGatewayAuthenticationForPublicApi() {
        MockHttpServletRequest request = new MockHttpServletRequest(
                "GET", "/api/workflow/process-instance/my-initiated");

        assertFalse(filter.shouldNotFilter(request));
    }
}
