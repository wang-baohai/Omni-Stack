package com.omni.auth.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import java.io.IOException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

/**
 * {@link SasConsentOriginFilter} 单元测试（WP-015 G01 CSRF 最小修正回归）。
 *
 * <p>覆盖三类关键行为：浏览器跨站 consent 提交拒绝、同源 consent 提交放行、
 * 非浏览器客户端（无 Origin 头）放行；另覆盖 X-Forwarded-* 网关转发语义。</p>
 *
 * @author Omni-Stack Team
 */
class SasConsentOriginFilterTest {

    private SasConsentOriginFilter filter;
    private FilterChain chain;
    private MockHttpServletRequest request;
    private MockHttpServletResponse response;

    @BeforeEach
    void setUp() {
        filter = new SasConsentOriginFilter();
        chain = mock(FilterChain.class);
        request = new MockHttpServletRequest();
        response = new MockHttpServletResponse();
    }

    @Test
    @DisplayName("跨站 Origin 的 device_verification consent POST 被拒绝（403）")
    void crossSiteOriginRejected() throws ServletException, IOException {
        request.setMethod("POST");
        request.setRequestURI("/oauth2/device_verification");
        request.addHeader("Origin", "http://evil.example");
        request.addHeader("X-Forwarded-Host", "localhost:8102");
        request.addHeader("X-Forwarded-Proto", "http");

        filter.doFilterInternal(request, response, chain);

        assertThat(response.getStatus()).isEqualTo(403);
        verify(chain, never()).doFilter(any(), any());
    }

    @Test
    @DisplayName("同源 Origin（经网关 X-Forwarded-Host）的 consent POST 放行")
    void sameOriginViaForwardedHostAllowed() throws ServletException, IOException {
        request.setMethod("POST");
        request.setRequestURI("/oauth2/device_verification");
        request.addHeader("Origin", "http://localhost:8102");
        request.addHeader("X-Forwarded-Host", "localhost:8102");
        request.addHeader("X-Forwarded-Proto", "http");

        filter.doFilterInternal(request, response, chain);

        assertThat(response.getStatus()).isEqualTo(200);
        verify(chain).doFilter(request, response);
    }

    @Test
    @DisplayName("同源 Origin（Host 头直连语义）的 authorize consent POST 放行")
    void sameOriginViaHostHeaderAllowed() throws ServletException, IOException {
        request.setMethod("POST");
        request.setRequestURI("/oauth2/authorize");
        request.addHeader("Origin", "http://localhost:3000");
        request.setLocalPort(3000);
        request.addHeader("Host", "localhost:3000");

        filter.doFilterInternal(request, response, chain);

        assertThat(response.getStatus()).isEqualTo(200);
        verify(chain).doFilter(request, response);
    }

    @Test
    @DisplayName("无 Origin 头的非浏览器客户端 consent POST 放行（不破坏 device flow/自动化测试）")
    void noOriginAllowed() throws ServletException, IOException {
        request.setMethod("POST");
        request.setRequestURI("/oauth2/device_verification");

        filter.doFilterInternal(request, response, chain);

        assertThat(response.getStatus()).isEqualTo(200);
        verify(chain).doFilter(request, response);
    }

    @Test
    @DisplayName("GET 请求不在校验范围（consent 表单渲染不受影响）")
    void getRequestBypassed() throws ServletException, IOException {
        request.setMethod("GET");
        request.setRequestURI("/oauth2/device_verification");
        request.addHeader("Origin", "http://evil.example");

        filter.doFilterInternal(request, response, chain);

        assertThat(response.getStatus()).isEqualTo(200);
        verify(chain).doFilter(request, response);
    }

    @Test
    @DisplayName("非 consent 端点的 POST 不在校验范围（token/revoke 等客户端凭证端点不受影响）")
    void otherEndpointBypassed() throws ServletException, IOException {
        request.setMethod("POST");
        request.setRequestURI("/oauth2/token");
        request.addHeader("Origin", "http://evil.example");

        filter.doFilterInternal(request, response, chain);

        assertThat(response.getStatus()).isEqualTo(200);
        verify(chain).doFilter(request, response);
    }

    @Test
    @DisplayName("X-Forwarded-Host 多值时取第一段比较")
    void forwardedHostMultiValueTakesFirst() throws ServletException, IOException {
        request.setMethod("POST");
        request.setRequestURI("/oauth2/device_verification");
        request.addHeader("Origin", "http://localhost:8102");
        request.addHeader("X-Forwarded-Host", "localhost:8102, evil.example");

        filter.doFilterInternal(request, response, chain);

        assertThat(response.getStatus()).isEqualTo(200);
        verify(chain).doFilter(request, response);
    }

    @Test
    @DisplayName("端口不匹配视为跨站（同 host 不同端口拒绝）")
    void portMismatchRejected() throws ServletException, IOException {
        request.setMethod("POST");
        request.setRequestURI("/oauth2/device_verification");
        request.addHeader("Origin", "http://localhost:9999");
        request.addHeader("X-Forwarded-Host", "localhost:8102");

        filter.doFilterInternal(request, response, chain);

        assertThat(response.getStatus()).isEqualTo(403);
        verify(chain, never()).doFilter(any(), any());
    }

    @Test
    @DisplayName("默认端口归一化：Origin 未声明端口与期望 80 端口匹配")
    void defaultPortNormalized() throws ServletException, IOException {
        request.setMethod("POST");
        request.setRequestURI("/oauth2/device_verification");
        request.addHeader("Origin", "http://app.example.com");
        request.addHeader("X-Forwarded-Host", "app.example.com:80");

        filter.doFilterInternal(request, response, chain);

        assertThat(response.getStatus()).isEqualTo(200);
        verify(chain).doFilter(request, response);
    }

    @Test
    @DisplayName("originMatches 工具方法：解析失败返回 false")
    void malformedOriginFailsClosed() {
        assertThat(SasConsentOriginFilter.originMatches("not-a-uri", "http", "localhost:8102")).isFalse();
        assertThat(SasConsentOriginFilter.originMatches("http://localhost:8102", null, "localhost:8102")).isFalse();
        assertThat(SasConsentOriginFilter.originMatches("http://localhost:8102", "http", null)).isFalse();
    }
}
