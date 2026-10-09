package com.omni.common.service.xss;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.omni.common.core.security.XssConfigProvider;
import com.omni.common.core.security.XssSettings;
import com.omni.common.service.config.ServiceIdentityProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.time.Duration;
import java.util.List;

/**
 * 共享的 Redis 缓存、Auth 回源和本地基线 XSS 配置提供者。
 *
 * @author Omni-Stack Team
 */
@Slf4j
@RequiredArgsConstructor
public class CachedServiceXssConfigProvider implements XssConfigProvider {

    private static final Duration CACHE_TTL = Duration.ofMinutes(30);

    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;
    private final XssSettingsResolver settingsResolver;
    private final XssSettingsFallback settingsFallback;
    private final ServiceIdentityProperties properties;

    /** {@inheritDoc} */
    @Override
    public XssSettings getXssSettings(Long tenantId) {
        try {
            String enabled = redisTemplate.opsForValue().get("xss:enabled:" + tenantId);
            String rulesJson = redisTemplate.opsForValue().get("xss:rules:" + tenantId);
            if (enabled != null && rulesJson != null) {
                List<XssSettings.XssRule> rules = objectMapper.readValue(
                        rulesJson, new TypeReference<List<XssSettings.XssRule>>() { });
                return XssSettings.builder().enabled(Boolean.parseBoolean(enabled)).rules(rules).build();
            }
        } catch (Exception exception) {
            log.warn("{} XSS 缓存读取或解析失败，将回源 Auth：tenantId={}, message={}",
                    properties.getDisplayName(), tenantId, exception.getMessage());
        }
        try {
            XssSettings settings = settingsResolver.resolve(tenantId);
            if (settings != null) {
                cacheAuthoritative(tenantId, settings);
                return settings;
            }
        } catch (RuntimeException exception) {
            log.error("{} XSS 配置回源失败，启用基线防护：tenantId={}, message={}",
                    properties.getDisplayName(), tenantId, exception.getMessage());
        }
        return settingsFallback.get();
    }

    private void cacheAuthoritative(Long tenantId, XssSettings settings) {
        try {
            redisTemplate.opsForValue().set("xss:enabled:" + tenantId,
                    Boolean.toString(settings.isEnabled()), CACHE_TTL);
            redisTemplate.opsForValue().set("xss:rules:" + tenantId,
                    objectMapper.writeValueAsString(settings.getRules() == null ? List.of() : settings.getRules()),
                    CACHE_TTL);
        } catch (Exception exception) {
            log.warn("{} XSS 权威配置写入缓存失败：tenantId={}, message={}",
                    properties.getDisplayName(), tenantId, exception.getMessage());
        }
    }
}
