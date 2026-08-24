package com.omni.srm.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.omni.common.service.config.ServiceIdentityProperties;
import com.omni.common.service.identity.ServicePathPolicy;
import com.omni.common.service.internal.InternalApiAuthenticationFilter;
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

    private static final String TOKEN = "srm-internal-test-token-0123456789abcdef";

    /** 清理 Spring Security 上下文。 */
    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    /** 未配置共享令牌时必须失败关闭。 */
    @Test
    void shouldFailClosedWhenExpectedTokenIsMissing() throws Exception {
        InternalApiAuthenticationFilter filter = filter("");
        MockHttpServletRequest request = request(null);
        MockHttpServletResponse response = new MockHttpServletResponse();
        FilterChain chain = mock(FilterChain.class);

        filter.doFilter(request, response, chain);

        assertThat(response.getStatus()).isEqualTo(401);
        verify(chain, never()).doFilter(any(), any());
    }

    /** 缺少调用令牌时不得进入内部控制器。 */
    @Test
    void shouldRejectMissingRequestToken() throws Exception {
        InternalApiAuthenticationFilter filter = filter(TOKEN);
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
        InternalApiAuthenticationFilter filter = filter(TOKEN);
        MockHttpServletRequest request = request(TOKEN);
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

    private InternalApiAuthenticationFilter filter(String token) {
        ServiceIdentityProperties properties = new ServiceIdentityProperties();
        properties.setName("omni-srm");
        properties.setDisplayName("SRM");
        properties.getInternalApi().setToken(token);
        return new InternalApiAuthenticationFilter(
                properties, new ServicePathPolicy(properties), new ObjectMapper());
    }
}
