package com.omni.base.security;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.omni.common.core.security.XssConfigProvider;
import com.omni.common.core.security.XssSettings;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.List;

/**
 * XSS 配置提供者实现（Redis-only 策略）。
 * <p>
 * Base 服务不直接访问 auth 数据库，而是从 Redis 缓存读取 XSS 配置。
 * auth 服务作为 XSS 配置的权威来源，负责将数据写入 Redis。
 * </p>
 * <p>
 * 缓存未命中时返回关闭状态的默认配置（安全降级），并记录警告日志。
 * </p>
 *
 * @author Omni-Stack Team
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class XssConfigProviderImpl implements XssConfigProvider {

    private static final String CACHE_KEY_ENABLED = "xss:enabled:";
    private static final String CACHE_KEY_RULES = "xss:rules:";

    private final StringRedisTemplate stringRedisTemplate;
    private final ObjectMapper objectMapper;

    @Override
    public XssSettings getXssSettings(Long tenantId) {
        String enabledKey = CACHE_KEY_ENABLED + tenantId;
        String rulesKey = CACHE_KEY_RULES + tenantId;

        String cachedEnabled = stringRedisTemplate.opsForValue().get(enabledKey);
        String cachedRulesJson = stringRedisTemplate.opsForValue().get(rulesKey);

        if (cachedEnabled != null && cachedRulesJson != null) {
            try {
                boolean enabled = Boolean.parseBoolean(cachedEnabled);
                List<XssSettings.XssRule> rules = objectMapper.readValue(
                        cachedRulesJson, new TypeReference<List<XssSettings.XssRule>>() {});
                return XssSettings.builder()
                        .enabled(enabled)
                        .rules(rules)
                        .build();
            } catch (JsonProcessingException e) {
                log.warn("反序列化 XSS 规则缓存失败，租户 {}: {}", tenantId, e.getMessage());
            }
        }

        // 缓存未命中：安全降级，返回关闭状态
        log.warn("XSS 配置缓存未命中，租户 {}，返回默认关闭状态。请确认 auth 服务已启动。", tenantId);
        return XssSettings.builder()
                .enabled(false)
                .rules(Collections.emptyList())
                .build();
    }
}
