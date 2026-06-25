package com.omni.auth.security;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.omni.auth.entity.SysXssBlacklistRule;
import com.omni.auth.entity.SysXssConfig;
import com.omni.auth.mapper.SysXssBlacklistRuleMapper;
import com.omni.auth.mapper.SysXssConfigMapper;
import com.omni.common.core.security.XssConfigProvider;
import com.omni.common.core.security.XssSettings;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

/**
 * XSS 配置提供者实现，优先从 Redis 缓存读取配置，缓存未命中时回源数据库查询。
 *
 * <p>Redis 缓存策略：
 * <ul>
 *   <li>{@code xss:enabled:{tenantId}} — 全局开关（字符串 "true" / "false"）</li>
 *   <li>{@code xss:rules:{tenantId}} — 已启用规则的 JSON 数组</li>
 * </ul>
 * 缓存 TTL 为 30 分钟，由管理端写操作主动清除缓存以保证一致性。</p>
 *
 * @author Omni-Stack Team
 * @see com.omni.common.core.security.XssConfigProvider
 * @see com.omni.common.core.security.XssSettings
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class XssConfigProviderImpl implements XssConfigProvider {

    private static final String CACHE_KEY_ENABLED = "xss:enabled:";
    private static final String CACHE_KEY_RULES = "xss:rules:";
    private static final long CACHE_TTL_MINUTES = 30;

    /** XSS 防护配置 Mapper */
    private final SysXssConfigMapper sysXssConfigMapper;

    /** XSS 黑名单规则 Mapper */
    private final SysXssBlacklistRuleMapper sysXssBlacklistRuleMapper;

    /** Redis 操作模板 */
    private final StringRedisTemplate stringRedisTemplate;

    /** Jackson JSON 序列化器 */
    private final ObjectMapper objectMapper;

    /**
     * {@inheritDoc}
     *
     * <p>优先从 Redis 读取全局开关和已启用规则列表，缓存命中时直接反序列化返回；
     * 缓存未命中时回源数据库查询并将结果写入 Redis（TTL 30 分钟）。
     * 若数据库中未找到租户配置，则返回关闭状态的默认配置。</p>
     *
     * @param tenantId 租户 ID
     * @return XSS 防护配置，包含全局开关和已启用规则列表
     */
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
                log.warn("反序列化 XSS 规则缓存失败，租户 {}，回源查询数据库: {}", tenantId, e.getMessage());
            }
        }

        return loadFromDbAndCache(tenantId, enabledKey, rulesKey);
    }

    /**
     * 从数据库加载 XSS 配置并写入 Redis 缓存。
     *
     * @param tenantId   租户 ID
     * @param enabledKey 全局开关缓存 key
     * @param rulesKey   规则列表缓存 key
     * @return XSS 防护配置
     */
    private XssSettings loadFromDbAndCache(Long tenantId, String enabledKey, String rulesKey) {
        SysXssConfig config = sysXssConfigMapper.selectOne(
                new LambdaQueryWrapper<SysXssConfig>()
                        .eq(SysXssConfig::getTenantId, tenantId));

        if (config == null) {
            return XssSettings.builder()
                    .enabled(false)
                    .rules(Collections.emptyList())
                    .build();
        }

        boolean enabled = config.getEnabled() != null && config.getEnabled() == 1;

        List<XssSettings.XssRule> rules = Collections.emptyList();
        if (enabled) {
            List<SysXssBlacklistRule> ruleEntities = sysXssBlacklistRuleMapper.selectList(
                    new LambdaQueryWrapper<SysXssBlacklistRule>()
                            .eq(SysXssBlacklistRule::getTenantId, tenantId)
                            .eq(SysXssBlacklistRule::getEnabled, 1)
                            .orderByAsc(SysXssBlacklistRule::getSortOrder));

            rules = ruleEntities.stream()
                    .map(entity -> XssSettings.XssRule.builder()
                            .id(entity.getId())
                            .ruleType(entity.getRuleType())
                            .pattern(entity.getPattern())
                            .build())
                    .collect(Collectors.toList());
        }

        XssSettings settings = XssSettings.builder()
                .enabled(enabled)
                .rules(rules)
                .build();

        try {
            stringRedisTemplate.opsForValue().set(enabledKey, String.valueOf(enabled), CACHE_TTL_MINUTES, TimeUnit.MINUTES);
            stringRedisTemplate.opsForValue().set(rulesKey, objectMapper.writeValueAsString(rules), CACHE_TTL_MINUTES, TimeUnit.MINUTES);
        } catch (JsonProcessingException e) {
            log.warn("序列化 XSS 规则写入 Redis 缓存失败，租户 {}: {}", tenantId, e.getMessage());
        }

        return settings;
    }
}
