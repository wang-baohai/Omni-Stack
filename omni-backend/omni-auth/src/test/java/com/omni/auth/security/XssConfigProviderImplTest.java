package com.omni.auth.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.omni.auth.service.XssConfigService;
import com.omni.common.core.security.XssSettings;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * {@link XssConfigProviderImpl} 单元测试。
 */
@ExtendWith(MockitoExtension.class)
class XssConfigProviderImplTest {

    /** XSS 配置服务 */
    @Mock
    private XssConfigService xssConfigService;

    /** Redis 模板 */
    @Mock
    private StringRedisTemplate stringRedisTemplate;

    /** Redis 字符串操作 */
    @Mock
    private ValueOperations<String, String> valueOperations;

    /**
     * 缓存中的字符串 false 是显式关闭状态，不应被当作缓存未命中。
     */
    @Test
    void should_distinguish_explicit_disabled_cache_from_cache_miss() {
        when(stringRedisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.get("xss:enabled:8")).thenReturn("false");
        when(valueOperations.get("xss:rules:8")).thenReturn("[]");
        XssConfigProviderImpl provider = new XssConfigProviderImpl(
                xssConfigService, stringRedisTemplate, new ObjectMapper());

        XssSettings result = provider.getXssSettings(8L);

        assertThat(result.isEnabled()).isFalse();
        assertThat(result.getRules()).isEqualTo(List.of());
        verify(xssConfigService, never()).getAuthoritativeSettings(8L);
    }
}
