package com.omni.workflow.security;

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
 * 工作流服务 XSS 配置提供者。
 * <p>
 * 工作流服务不直接维护 XSS 配置表，运行时从 auth 服务写入的 Redis 缓存读取租户级配置。
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

    /** {@inheritDoc} */
    @Override
    public XssSettings getXssSettings(Long tenantId) {
        String enabledKey = CACHE_KEY_ENABLED + tenantId;
        String rulesKey = CACHE_KEY_RULES + tenantId;

        String cachedEnabled = stringRedisTemplate.opsForValue().get(enabledKey);
        String cachedRulesJson = stringRedisTemplate.opsForValue().get(rulesKey);
        if (cachedEnabled != null && cachedRulesJson != null) {
            try {
                List<XssSettings.XssRule> rules = objectMapper.readValue(
                        cachedRulesJson, new TypeReference<List<XssSettings.XssRule>>() {});
                return XssSettings.builder()
                        .enabled(Boolean.parseBoolean(cachedEnabled))
                        .rules(rules)
                        .build();
            } catch (JsonProcessingException e) {
                log.warn("反序列化工作流 XSS 规则缓存失败: tenantId={}, message={}", tenantId, e.getMessage());
            }
        }

        log.warn("工作流 XSS 配置缓存未命中: tenantId={}", tenantId);
        return XssSettings.builder()
                .enabled(false)
                .rules(Collections.emptyList())
                .build();
    }
}
