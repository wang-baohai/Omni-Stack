package com.omni.srm.security;

import com.omni.common.mqlog.filter.InternalApiAuthFilter;
import jakarta.servlet.FilterChain;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

/** 容器级内部 API 令牌过滤器安全回归测试。 */
class InternalApiAuthFilterTest {

    /** 清理 Spring Security 上下文。 */
    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    /** 未配置共享令牌时必须失败关闭。 */
    @Test
    void shouldFailClosedWhenExpectedTokenIsMissing() throws Exception {
        InternalApiAuthFilter filter = new InternalApiAuthFilter("");
        MockHttpServletRequest request = request(null);
        MockHttpServletResponse response = new MockHttpServletResponse();
        FilterChain chain = mock(FilterChain.class);

        filter.doFilter(request, response, chain);

        assertThat(response.getStatus()).isEqualTo(503);
        verify(chain, never()).doFilter(any(), any());
    }

    /** 缺少调用令牌时不得进入内部控制器。 */
    @Test
    void shouldRejectMissingRequestToken() throws Exception {
        InternalApiAuthFilter filter = new InternalApiAuthFilter("shared-secret");
        MockHttpServletRequest request = request(null);
        MockHttpServletResponse response = new MockHttpServletResponse();
        FilterChain chain = mock(FilterChain.class);

        filter.doFilter(request, response, chain);

        assertThat(response.getStatus()).isEqualTo(401);
        verify(chain, never()).doFilter(any(), any());
    }

    /** 合法令牌仅在调用期间建立内部服务身份，并在结束后清理。 */
    @Test
    void shouldAuthenticateAndClearContextForValidToken() throws Exception {
        InternalApiAuthFilter filter = new InternalApiAuthFilter("shared-secret");
        MockHttpServletRequest request = request("shared-secret");
        MockHttpServletResponse response = new MockHttpServletResponse();
        FilterChain chain = mock(FilterChain.class);
        AtomicReference<Authentication> observed = new AtomicReference<>();
        doAnswer(invocation -> {
            observed.set(SecurityContextHolder.getContext().getAuthentication());
            return null;
        }).when(chain).doFilter(any(), any());

        filter.doFilter(request, response, chain);

        assertThat(observed.get()).isNotNull();
        assertThat(observed.get().getAuthorities()).extracting("authority")
                .containsExactly("ROLE_INTERNAL_SERVICE");
        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
        verify(chain).doFilter(any(), any());
    }

    private MockHttpServletRequest request(String token) {
        MockHttpServletRequest request = new MockHttpServletRequest(
                "POST", "/api/internal/supplier/batch");
        if (token != null) {
            request.addHeader("X-Internal-Token", token);
        }
        return request;
    }
}
