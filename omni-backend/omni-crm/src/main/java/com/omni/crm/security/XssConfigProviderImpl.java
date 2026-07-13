package com.omni.crm.security;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.omni.common.core.result.R;
import com.omni.common.core.security.XssConfigProvider;
import com.omni.common.core.security.XssSettings;
import com.omni.crm.client.AuthInternalClient;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.util.List;
import java.time.Duration;

/**
 * CRM XSS 配置提供者，缓存未命中时回源 Auth，Auth 不可用时启用基线规则。
 *
 * @author Omni-Stack Team
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class XssConfigProviderImpl implements XssConfigProvider {

    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;
    private final AuthInternalClient authInternalClient;

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
            log.warn("CRM XSS 缓存读取或解析失败，将回源 Auth：tenantId={}, message={}", tenantId, exception.getMessage());
        }
        try {
            R<XssSettings> response = authInternalClient.getXssSettings(tenantId);
            if (response != null && response.getCode() == 200 && response.getData() != null) {
                cacheAuthoritative(tenantId, response.getData());
                return response.getData();
            }
        } catch (RuntimeException exception) {
            log.error("CRM XSS 配置回源失败，启用基线防护：tenantId={}, message={}",
                    tenantId, exception.getMessage());
        }
        return baselineSettings();
    }

    private void cacheAuthoritative(Long tenantId, XssSettings settings) {
        try {
            Duration ttl = Duration.ofMinutes(30);
            redisTemplate.opsForValue().set("xss:enabled:" + tenantId,
                    Boolean.toString(settings.isEnabled()), ttl);
            redisTemplate.opsForValue().set("xss:rules:" + tenantId,
                    objectMapper.writeValueAsString(settings.getRules() == null ? List.of() : settings.getRules()), ttl);
        } catch (Exception exception) {
            log.warn("CRM XSS 权威配置写入缓存失败：tenantId={}, message={}", tenantId, exception.getMessage());
        }
    }

    private XssSettings baselineSettings() {
        return XssSettings.builder().enabled(true).rules(List.of(
                rule(-1L, "HTML_TAG", "script|iframe|object|embed|style"),
                rule(-2L, "EVENT_HANDLER", "on[a-zA-Z]+"),
                rule(-3L, "DANGEROUS_PROTOCOL", "javascript:|vbscript:|data:text/html")
        )).build();
    }

    private XssSettings.XssRule rule(Long id, String type, String pattern) {
        return XssSettings.XssRule.builder().id(id).ruleType(type).pattern(pattern).build();
    }
}
