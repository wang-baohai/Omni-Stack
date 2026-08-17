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
