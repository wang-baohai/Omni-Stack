package com.omni.common.service.internal;

import com.omni.common.web.TraceIdFilter;
import feign.RequestTemplate;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class InternalFeignHeadersFactoryTest {

    @AfterEach
    void clearMdc() {
        MDC.clear();
    }

    @Test
    void shouldPropagateOnlyExplicitTokenTenantAndTrace() {
        MDC.put(TraceIdFilter.MDC_KEY, "0123456789abcdef");
        RequestTemplate template = new RequestTemplate();

        new InternalFeignHeadersFactory()
                .create("starter-test-token-0123456789abcdef", () -> 3L)
                .apply(template);

        assertThat(template.headers().get("X-Internal-Token"))
                .containsExactly("starter-test-token-0123456789abcdef");
        assertThat(template.headers().get("X-Tenant-Id")).containsExactly("3");
        assertThat(template.headers().get("X-Trace-Id")).containsExactly("0123456789abcdef");
    }

    @Test
    void shouldRejectMissingTokenBeforeCreatingInterceptor() {
        assertThatThrownBy(() -> new InternalFeignHeadersFactory().create(""))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Token 不能为空");
    }
}
