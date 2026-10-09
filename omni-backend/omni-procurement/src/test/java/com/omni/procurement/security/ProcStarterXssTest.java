package com.omni.procurement.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.omni.common.service.config.ServiceIdentityProperties;
import com.omni.common.service.xss.CachedServiceXssConfigProvider;
import com.omni.common.service.xss.DefaultXssSettingsFallback;
import com.omni.common.service.xss.XssSettingsResolver;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** Procurement 采用公共 Starter 后的 XSS 权威配置与安全基线测试。 */
class ProcStarterXssTest {

    /** 显式关闭的缓存值不能被误判为缓存未命中。 */
    @Test
    void shouldRespectExplicitDisabledCache() {
        StringRedisTemplate redis = mock(StringRedisTemplate.class);
        @SuppressWarnings("unchecked")
        ValueOperations<String, String> operations = mock(ValueOperations.class);
        XssSettingsResolver resolver = mock(XssSettingsResolver.class);
        when(redis.opsForValue()).thenReturn(operations);
        when(operations.get("xss:enabled:8")).thenReturn("false");
        when(operations.get("xss:rules:8")).thenReturn("[]");

        var settings = provider(redis, resolver).getXssSettings(8L);

        assertThat(settings.isEnabled()).isFalse();
        assertThat(settings.getRules()).isEmpty();
        verify(resolver, never()).resolve(8L);
    }

    /** Redis 与 Auth 同时不可用时必须启用内置基线防护。 */
    @Test
    void shouldEnableBaselineProtectionWhenDependenciesFail() {
        StringRedisTemplate redis = mock(StringRedisTemplate.class);
        XssSettingsResolver resolver = mock(XssSettingsResolver.class);
        when(redis.opsForValue()).thenThrow(new IllegalStateException("redis unavailable"));
        when(resolver.resolve(9L)).thenThrow(new IllegalStateException("auth unavailable"));

        var settings = provider(redis, resolver).getXssSettings(9L);

        assertThat(settings.isEnabled()).isTrue();
        assertThat(settings.getRules()).extracting("ruleType")
                .containsExactly("HTML_TAG", "EVENT_HANDLER", "DANGEROUS_PROTOCOL");
    }

    private CachedServiceXssConfigProvider provider(
            StringRedisTemplate redis, XssSettingsResolver resolver) {
        ServiceIdentityProperties properties = new ServiceIdentityProperties();
        properties.setDisplayName("Procurement");
        return new CachedServiceXssConfigProvider(redis, new ObjectMapper(), resolver,
                new DefaultXssSettingsFallback(), properties);
    }
}
