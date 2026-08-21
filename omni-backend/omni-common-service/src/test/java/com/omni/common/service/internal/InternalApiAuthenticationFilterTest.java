package com.omni.common.service.internal;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.omni.common.service.config.ServiceIdentityProperties;
import com.omni.common.service.identity.ServicePathPolicy;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.context.SecurityContextHolder;

import static org.assertj.core.api.Assertions.assertThat;

class InternalApiAuthenticationFilterTest {

    private static final String TOKEN = "starter-test-token-0123456789abcdef";

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void shouldRejectMissingToken() throws Exception {
        InternalApiAuthenticationFilter filter = filter();
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/internal/test");
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, (req, res) -> { });

        assertThat(response.getStatus()).isEqualTo(401);
    }

    @Test
    void shouldEstablishInternalRoleAndClearIt() throws Exception {
        InternalApiAuthenticationFilter filter = filter();
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/internal/test");
        request.addHeader("X-Internal-Token", TOKEN);
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, (req, res) -> assertThat(
                SecurityContextHolder.getContext().getAuthentication().getAuthorities())
                .extracting("authority").containsExactly("ROLE_INTERNAL_SERVICE"));

        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
    }

    private InternalApiAuthenticationFilter filter() {
        ServiceIdentityProperties properties = new ServiceIdentityProperties();
        properties.setName("omni-test");
        properties.setDisplayName("Test");
        properties.getInternalApi().setToken(TOKEN);
        return new InternalApiAuthenticationFilter(
                properties, new ServicePathPolicy(properties), new ObjectMapper());
    }
}
