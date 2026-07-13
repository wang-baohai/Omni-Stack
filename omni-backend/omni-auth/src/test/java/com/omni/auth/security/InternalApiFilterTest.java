package com.omni.auth.security;

import jakarta.servlet.FilterChain;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.context.SecurityContextHolder;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

/**
 * {@link InternalApiFilter} 单元测试。
 */
class InternalApiFilterTest {

    /**
     * 每个测试后清理安全上下文。
     */
    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    /**
     * 有效内部令牌应建立内部服务身份并继续过滤器链。
     *
     * @throws Exception 过滤器执行异常
     */
    @Test
    void should_authenticate_internal_request_with_valid_token() throws Exception {
        InternalApiFilter filter = new InternalApiFilter("shared-secret");
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/internal/data-scopes/7");
        request.addHeader("X-Internal-Token", "shared-secret");
        MockHttpServletResponse response = new MockHttpServletResponse();
        FilterChain chain = mock(FilterChain.class);
        doAnswer(invocation -> {
            assertThat(SecurityContextHolder.getContext().getAuthentication().getAuthorities())
                    .extracting("authority")
                    .containsExactly("ROLE_INTERNAL_SERVICE");
            return null;
        }).when(chain).doFilter(request, response);

        filter.doFilter(request, response, chain);

        verify(chain).doFilter(request, response);
        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
    }

    /**
     * 无效内部令牌应返回 401 且不得进入下游链路。
     *
     * @throws Exception 过滤器执行异常
     */
    @Test
    void should_reject_internal_request_with_invalid_token() throws Exception {
        InternalApiFilter filter = new InternalApiFilter("shared-secret");
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/internal/xss/settings");
        request.addHeader("X-Internal-Token", "wrong-secret");
        MockHttpServletResponse response = new MockHttpServletResponse();
        FilterChain chain = mock(FilterChain.class);

        filter.doFilter(request, response, chain);

        assertThat(response.getStatus()).isEqualTo(401);
        assertThat(response.getContentAsString()).contains("Invalid internal token");
        verify(chain, never()).doFilter(request, response);
    }

    /**
     * 未配置内部令牌时应返回 503，表示内部依赖配置不可用。
     *
     * @throws Exception 过滤器执行异常
     */
    @Test
    void should_return_service_unavailable_when_token_is_not_configured() throws Exception {
        InternalApiFilter filter = new InternalApiFilter("");
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/internal/xss/settings");
        MockHttpServletResponse response = new MockHttpServletResponse();
        FilterChain chain = mock(FilterChain.class);

        filter.doFilter(request, response, chain);

        assertThat(response.getStatus()).isEqualTo(503);
        assertThat(response.getContentAsString()).contains("Internal API not configured");
        verify(chain, never()).doFilter(request, response);
    }
}
