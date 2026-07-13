package com.omni.auth.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.omni.auth.dto.BlacklistRuleVO;
import com.omni.auth.dto.CreateXssRuleRequest;
import com.omni.auth.dto.UpdateXssRuleRequest;
import com.omni.auth.dto.XssSettingsVO;
import com.omni.auth.entity.SysXssBlacklistRule;
import com.omni.auth.entity.SysXssConfig;
import com.omni.auth.mapper.SysXssBlacklistRuleMapper;
import com.omni.auth.mapper.SysXssConfigMapper;
import com.omni.auth.service.XssConfigService;
import com.omni.common.core.result.BusinessException;
import com.omni.common.core.result.PageResult;
import com.omni.common.core.security.XssSettings;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;
import java.util.stream.Collectors;

/**
 * XSS 防护配置服务实现，基于 MyBatis-Plus 提供全局开关和黑名单规则的管理操作。
 *
 * <p>所有写操作均会同步清除 Redis 缓存，保证配置变更即时生效。</p>
 *
 * @author Omni-Stack Team
 * @see XssConfigService
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class XssConfigServiceImpl implements XssConfigService {

    /** XSS 防护配置 Mapper */
    private final SysXssConfigMapper sysXssConfigMapper;

    /** XSS 黑名单规则 Mapper */
    private final SysXssBlacklistRuleMapper sysXssBlacklistRuleMapper;

    /** Redis 操作模板 */
    private final StringRedisTemplate stringRedisTemplate;

    /**
     * {@inheritDoc}
     *
     * <p>查询租户的 XSS 全局配置（不存在则创建默认关闭配置），
     * 再查询该租户下全部黑名单规则，按 {@code sort_order} 升序排列。</p>
     */
    @Override
    public XssSettingsVO getSettings(Long tenantId) {
        SysXssConfig config = getOrCreateConfig(tenantId);

        List<SysXssBlacklistRule> rules = sysXssBlacklistRuleMapper.selectList(
                new LambdaQueryWrapper<SysXssBlacklistRule>()
                        .eq(SysXssBlacklistRule::getTenantId, tenantId)
                        .orderByAsc(SysXssBlacklistRule::getSortOrder));

        List<BlacklistRuleVO> ruleVOs = rules.stream()
                .map(this::toRuleVO)
                .collect(Collectors.toList());

        return XssSettingsVO.builder()
                .enabled(config.getEnabled() != null && config.getEnabled() == 1)
                .rules(ruleVOs)
                .build();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    @Transactional(readOnly = true)
    public XssSettings getAuthoritativeSettings(Long tenantId) {
        if (tenantId == null || tenantId <= 0) {
            throw new BusinessException(400, "租户 ID 必须为正整数");
        }

        SysXssConfig config = sysXssConfigMapper.selectOne(
                new LambdaQueryWrapper<SysXssConfig>()
                        .eq(SysXssConfig::getTenantId, tenantId));
        boolean enabled = config != null && Integer.valueOf(1).equals(config.getEnabled());
        if (!enabled) {
            return XssSettings.builder()
                    .enabled(false)
                    .rules(List.of())
                    .build();
        }

        List<XssSettings.XssRule> rules = sysXssBlacklistRuleMapper.selectList(
                        new LambdaQueryWrapper<SysXssBlacklistRule>()
                                .eq(SysXssBlacklistRule::getTenantId, tenantId)
                                .eq(SysXssBlacklistRule::getEnabled, 1)
                                .orderByAsc(SysXssBlacklistRule::getSortOrder))
                .stream()
                .map(entity -> XssSettings.XssRule.builder()
                        .id(entity.getId())
                        .ruleType(entity.getRuleType())
                        .pattern(entity.getPattern())
                        .build())
                .toList();

        return XssSettings.builder()
                .enabled(true)
                .rules(rules)
                .build();
    }

    /**
     * {@inheritDoc}
     *
     * <p>配置不存在时插入默认记录，存在则更新 {@code enabled} 字段。
     * 操作完成后清除 Redis 缓存。</p>
     */
    @Override
    @Transactional
    public void toggleGlobal(Long tenantId, boolean enabled, String operator) {
        SysXssConfig config = sysXssConfigMapper.selectOne(
                new LambdaQueryWrapper<SysXssConfig>()
                        .eq(SysXssConfig::getTenantId, tenantId));

        int enabledValue = enabled ? 1 : 0;

        if (config == null) {
            config = new SysXssConfig();
            config.setTenantId(tenantId);
            config.setEnabled(enabledValue);
            config.setCreateBy(operator);
            sysXssConfigMapper.insert(config);
        } else {
            config.setEnabled(enabledValue);
            config.setUpdateBy(operator);
            sysXssConfigMapper.updateById(config);
        }

        invalidateXssCache(tenantId);
        log.info("已切换租户 {} 的 XSS 全局开关为: {}", tenantId, enabled ? "开启" : "关闭");
    }

    /**
     * {@inheritDoc}
     *
     * <p>使用 MyBatis-Plus 分页插件，按 {@code sort_order} 升序排列。</p>
     */
    @Override
    public PageResult<BlacklistRuleVO> listRules(Long tenantId, int page, int size) {
        Page<SysXssBlacklistRule> mpPage = sysXssBlacklistRuleMapper.selectPage(
                new Page<>(page, size),
                new LambdaQueryWrapper<SysXssBlacklistRule>()
                        .eq(SysXssBlacklistRule::getTenantId, tenantId)
                        .orderByAsc(SysXssBlacklistRule::getSortOrder));

        List<BlacklistRuleVO> voList = mpPage.getRecords().stream()
                .map(this::toRuleVO)
                .collect(Collectors.toList());

        return new PageResult<>(voList, mpPage.getTotal(), mpPage.getSize(), mpPage.getCurrent());
    }

    /**
     * {@inheritDoc}
     *
     * <p>校验正则表达式合法性后插入数据库，操作完成后清除 Redis 缓存。</p>
     */
    @Override
    @Transactional
    public BlacklistRuleVO createRule(Long tenantId, CreateXssRuleRequest request, String operator) {
        validatePattern(request.getPattern());

        SysXssBlacklistRule rule = new SysXssBlacklistRule();
        rule.setTenantId(tenantId);
        rule.setRuleName(request.getRuleName());
        rule.setRuleType(request.getRuleType());
        rule.setPattern(request.getPattern());
        rule.setDescription(request.getDescription());
        rule.setSortOrder(request.getSortOrder() != null ? request.getSortOrder() : 0);
        rule.setEnabled(1);
        rule.setCreateBy(operator);

        sysXssBlacklistRuleMapper.insert(rule);
        invalidateXssCache(tenantId);
        log.info("已为租户 {} 创建 XSS 黑名单规则: {}", tenantId, rule.getRuleName());
        return toRuleVO(rule);
    }

    /**
     * {@inheritDoc}
     *
     * <p>仅更新非 null 字段，若 pattern 变更则重新校验正则表达式合法性。
     * 操作完成后清除 Redis 缓存。</p>
     */
    @Override
    @Transactional
    public BlacklistRuleVO updateRule(Long id, UpdateXssRuleRequest request, String operator) {
        SysXssBlacklistRule rule = sysXssBlacklistRuleMapper.selectById(id);
        if (rule == null) {
            throw new BusinessException(404, "XSS 黑名单规则不存在");
        }

        if (request.getRuleName() != null) {
            rule.setRuleName(request.getRuleName());
        }
        if (request.getRuleType() != null) {
            rule.setRuleType(request.getRuleType());
        }
        if (request.getPattern() != null) {
            validatePattern(request.getPattern());
            rule.setPattern(request.getPattern());
        }
        if (request.getDescription() != null) {
            rule.setDescription(request.getDescription());
        }
        if (request.getSortOrder() != null) {
            rule.setSortOrder(request.getSortOrder());
        }
        if (request.getEnabled() != null) {
            rule.setEnabled(request.getEnabled());
        }

        rule.setUpdateBy(operator);
        sysXssBlacklistRuleMapper.updateById(rule);
        invalidateXssCache(rule.getTenantId());
        log.info("已更新 XSS 黑名单规则 ID: {}", id);
        return toRuleVO(rule);
    }

    /**
     * {@inheritDoc}
     *
     * <p>先查询规则获取租户 ID 用于缓存清除，执行删除后清除 Redis 缓存。</p>
     */
    @Override
    @Transactional
    public void deleteRule(Long id) {
        SysXssBlacklistRule rule = sysXssBlacklistRuleMapper.selectById(id);
        if (rule == null) {
            throw new BusinessException(404, "XSS 黑名单规则不存在");
        }

        Long tenantId = rule.getTenantId();
        sysXssBlacklistRuleMapper.deleteById(id);
        invalidateXssCache(tenantId);
        log.info("已删除 XSS 黑名单规则 ID: {}", id);
    }

    /**
     * {@inheritDoc}
     *
     * <p>更新规则的 {@code enabled} 字段，操作完成后清除 Redis 缓存。</p>
     */
    @Override
    @Transactional
    public void toggleRule(Long id, boolean enabled) {
        SysXssBlacklistRule rule = sysXssBlacklistRuleMapper.selectById(id);
        if (rule == null) {
            throw new BusinessException(404, "XSS 黑名单规则不存在");
        }

        rule.setEnabled(enabled ? 1 : 0);
        sysXssBlacklistRuleMapper.updateById(rule);
        invalidateXssCache(rule.getTenantId());
        log.info("已切换 XSS 黑名单规则 {} 的状态为: {}", id, enabled ? "启用" : "禁用");
    }

    /**
     * 获取租户的 XSS 配置，不存在时创建默认关闭配置。
     *
     * @param tenantId 租户 ID
     * @return XSS 配置实体
     */
    private SysXssConfig getOrCreateConfig(Long tenantId) {
        SysXssConfig config = sysXssConfigMapper.selectOne(
                new LambdaQueryWrapper<SysXssConfig>()
                        .eq(SysXssConfig::getTenantId, tenantId));
        if (config == null) {
            config = new SysXssConfig();
            config.setTenantId(tenantId);
            config.setEnabled(0);
            sysXssConfigMapper.insert(config);
            log.info("已为租户 {} 创建默认 XSS 配置（关闭状态）", tenantId);
        }
        return config;
    }

    /**
     * 校验正则表达式语法合法性。
     *
     * @param pattern 正则表达式字符串
     * @throws BusinessException 正则表达式语法错误时抛出
     */
    private void validatePattern(String pattern) {
        try {
            Pattern.compile(pattern);
        } catch (PatternSyntaxException e) {
            throw new BusinessException(400, "XSS 规则正则表达式语法错误: " + e.getDescription());
        }
    }

    /**
     * 清除租户的 XSS 相关 Redis 缓存。
     *
     * <p>同时删除 {@code xss:enabled:{tenantId}} 和 {@code xss:rules:{tenantId}} 两个 key。</p>
     *
     * @param tenantId 租户 ID
     */
    private void invalidateXssCache(Long tenantId) {
        stringRedisTemplate.delete("xss:enabled:" + tenantId);
        stringRedisTemplate.delete("xss:rules:" + tenantId);
    }

    /**
     * 将黑名单规则实体转换为视图对象。
     *
     * @param entity 黑名单规则实体
     * @return 黑名单规则视图对象
     */
    private BlacklistRuleVO toRuleVO(SysXssBlacklistRule entity) {
        return BlacklistRuleVO.builder()
                .id(entity.getId())
                .ruleName(entity.getRuleName())
                .ruleType(entity.getRuleType())
                .pattern(entity.getPattern())
                .enabled(entity.getEnabled())
                .description(entity.getDescription())
                .sortOrder(entity.getSortOrder())
                .build();
    }
}
