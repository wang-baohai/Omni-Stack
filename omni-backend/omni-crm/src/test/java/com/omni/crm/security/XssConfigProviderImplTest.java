package com.omni.crm.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.omni.crm.client.AuthInternalClient;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** CRM XSS 权威配置与失败关闭策略测试。 */
class XssConfigProviderImplTest {

    /** 显式关闭的缓存值不能被误判为缓存未命中。 */
    @Test
    void shouldRespectExplicitDisabledCache() {
        StringRedisTemplate redis = mock(StringRedisTemplate.class);
        @SuppressWarnings("unchecked")
        ValueOperations<String, String> operations = mock(ValueOperations.class);
        AuthInternalClient auth = mock(AuthInternalClient.class);
        when(redis.opsForValue()).thenReturn(operations);
        when(operations.get("xss:enabled:8")).thenReturn("false");
        when(operations.get("xss:rules:8")).thenReturn("[]");

        var settings = new XssConfigProviderImpl(redis, new ObjectMapper(), auth).getXssSettings(8L);

        assertThat(settings.isEnabled()).isFalse();
        assertThat(settings.getRules()).isEmpty();
        verify(auth, never()).getXssSettings(8L);
    }

    /** Redis 与 Auth 同时不可用时必须启用内置基线防护。 */
    @Test
    void shouldEnableBaselineProtectionWhenDependenciesFail() {
        StringRedisTemplate redis = mock(StringRedisTemplate.class);
        AuthInternalClient auth = mock(AuthInternalClient.class);
        when(redis.opsForValue()).thenThrow(new IllegalStateException("redis unavailable"));
        when(auth.getXssSettings(9L)).thenThrow(new IllegalStateException("auth unavailable"));

        var settings = new XssConfigProviderImpl(redis, new ObjectMapper(), auth).getXssSettings(9L);

        assertThat(settings.isEnabled()).isTrue();
        assertThat(settings.getRules()).extracting("ruleType")
                .containsExactly("HTML_TAG", "EVENT_HANDLER", "DANGEROUS_PROTOCOL");
    }
}
