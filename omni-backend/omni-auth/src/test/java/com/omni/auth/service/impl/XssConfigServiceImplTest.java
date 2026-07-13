package com.omni.auth.service.impl;

import com.omni.auth.entity.SysXssBlacklistRule;
import com.omni.auth.entity.SysXssConfig;
import com.omni.auth.mapper.SysXssBlacklistRuleMapper;
import com.omni.auth.mapper.SysXssConfigMapper;
import com.omni.common.core.security.XssSettings;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * {@link XssConfigServiceImpl} 单元测试。
 */
@ExtendWith(MockitoExtension.class)
class XssConfigServiceImplTest {

    /** 配置 Mapper */
    @Mock
    private SysXssConfigMapper sysXssConfigMapper;

    /** 规则 Mapper */
    @Mock
    private SysXssBlacklistRuleMapper sysXssBlacklistRuleMapper;

    /** Redis 模板 */
    @Mock
    private StringRedisTemplate stringRedisTemplate;

    /** 被测服务 */
    private XssConfigServiceImpl service;

    /**
     * 初始化被测对象。
     */
    @BeforeEach
    void setUp() {
        service = new XssConfigServiceImpl(
                sysXssConfigMapper, sysXssBlacklistRuleMapper, stringRedisTemplate);
    }

    /**
     * 修改配置后必须同时删除 enabled 和 rules 缓存键。
     */
    @Test
    void should_invalidate_enabled_and_rules_cache_keys() {
        SysXssConfig config = new SysXssConfig();
        config.setId(1L);
        config.setTenantId(8L);
        config.setEnabled(0);
        when(sysXssConfigMapper.selectOne(any())).thenReturn(config);

        service.toggleGlobal(8L, true, "admin");

        verify(stringRedisTemplate).delete("xss:enabled:8");
        verify(stringRedisTemplate).delete("xss:rules:8");
    }

    /**
     * 权威设置查询应直接保留数据库中的显式关闭状态且不访问 Redis。
     */
    @Test
    void should_return_explicit_disabled_state_from_database() {
        SysXssConfig config = new SysXssConfig();
        config.setTenantId(8L);
        config.setEnabled(0);
        when(sysXssConfigMapper.selectOne(any())).thenReturn(config);

        XssSettings result = service.getAuthoritativeSettings(8L);

        assertThat(result.isEnabled()).isFalse();
        assertThat(result.getRules()).isEmpty();
        verify(sysXssBlacklistRuleMapper, never()).selectList(any());
        verify(stringRedisTemplate, never()).opsForValue();
    }

    /**
     * 启用状态只应返回数据库中已启用的运行时规则。
     */
    @Test
    void should_return_authoritative_enabled_rules() {
        SysXssConfig config = new SysXssConfig();
        config.setTenantId(8L);
        config.setEnabled(1);
        SysXssBlacklistRule rule = new SysXssBlacklistRule();
        rule.setId(2L);
        rule.setRuleType("HTML_TAG");
        rule.setPattern("script");
        when(sysXssConfigMapper.selectOne(any())).thenReturn(config);
        when(sysXssBlacklistRuleMapper.selectList(any())).thenReturn(List.of(rule));

        XssSettings result = service.getAuthoritativeSettings(8L);

        assertThat(result.isEnabled()).isTrue();
        assertThat(result.getRules()).singleElement().satisfies(runtimeRule -> {
            assertThat(runtimeRule.getId()).isEqualTo(2L);
            assertThat(runtimeRule.getRuleType()).isEqualTo("HTML_TAG");
            assertThat(runtimeRule.getPattern()).isEqualTo("script");
        });
    }
}
