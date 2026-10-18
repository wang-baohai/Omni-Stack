package com.omni.auth.controller;

import com.omni.auth.config.AuthExceptionHandler;
import com.omni.auth.entity.SysXssBlacklistRule;
import com.omni.auth.mapper.SysXssBlacklistRuleMapper;
import com.omni.auth.mapper.SysXssConfigMapper;
import com.omni.auth.service.impl.XssConfigServiceImpl;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * XSS 规则写接口的租户边界回归测试。
 * <p>使用真实控制器和服务，Mapper 与 Redis 为隔离替身；不代表网关认证或数据库集成验收。</p>
 */
@ExtendWith(MockitoExtension.class)
class XssRuleTenantBoundaryTest {

    @Mock
    private SysXssConfigMapper configMapper;

    @Mock
    private SysXssBlacklistRuleMapper ruleMapper;

    @Mock
    private StringRedisTemplate redisTemplate;

    private MockMvc mvc;

    /** 初始化真实控制器、服务和已有业务异常映射。 */
    @BeforeEach
    void setUp() {
        var service = new XssConfigServiceImpl(configMapper, ruleMapper, redisTemplate);
        mvc = MockMvcBuilders.standaloneSetup(new XssConfigController(service))
                .setControllerAdvice(new AuthExceptionHandler())
                .build();
        SecurityContextHolder.getContext().setAuthentication(
                new TestingAuthenticationToken("operator", null,
                        "system:xssconfig:update", "system:xssconfig:delete"));
    }

    /** 清理线程安全上下文。 */
    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    /** 已有功能权限也不得修改另一租户的规则或清除其缓存。 */
    @ParameterizedTest
    @ValueSource(strings = {"update", "delete", "toggle"})
    void should_reject_rule_owned_by_another_tenant_without_writes(String operation) throws Exception {
        when(ruleMapper.selectById(901L)).thenReturn(rule(9L));

        mvc.perform(request(operation).header("X-Tenant-Id", 8L))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(404));

        verify(ruleMapper, never()).updateById(any(SysXssBlacklistRule.class));
        verify(ruleMapper, never()).deleteById(anyLong());
        verifyNoInteractions(redisTemplate);
    }

    /** 不存在与不属于当前租户的规则应有相同的业务失败语义。 */
    @ParameterizedTest
    @ValueSource(strings = {"update", "delete", "toggle"})
    void should_reject_missing_rule_without_writes(String operation) throws Exception {
        mvc.perform(request(operation).header("X-Tenant-Id", 8L))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(404));

        verify(ruleMapper, never()).updateById(any(SysXssBlacklistRule.class));
        verify(ruleMapper, never()).deleteById(anyLong());
        verifyNoInteractions(redisTemplate);
    }

    /** 同租户写入保持成功，并清除该租户的两项缓存。 */
    @ParameterizedTest
    @ValueSource(strings = {"update", "delete", "toggle"})
    void should_keep_same_tenant_write_and_cache_invalidation(String operation) throws Exception {
        when(ruleMapper.selectById(901L)).thenReturn(rule(8L));

        mvc.perform(request(operation).header("X-Tenant-Id", 8L))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));

        if ("delete".equals(operation)) {
            verify(ruleMapper).deleteById(901L);
        } else {
            verify(ruleMapper).updateById(any(SysXssBlacklistRule.class));
        }
        verify(redisTemplate).delete("xss:enabled:8");
        verify(redisTemplate).delete("xss:rules:8");
    }

    /** 构造三种现有写接口请求，保持路径和请求体契约不变。 */
    private MockHttpServletRequestBuilder request(String operation) {
        String path = "/api/auth/xss-config/rules/901";
        return switch (operation) {
            case "update" -> put(path).contentType(MediaType.APPLICATION_JSON)
                    .content("{\"ruleName\":\"renamed\"}");
            case "delete" -> delete(path);
            case "toggle" -> put(path + "/toggle").param("enabled", "false");
            default -> throw new IllegalArgumentException(operation);
        };
    }

    /** 构造只存在于内存中的规则数据。 */
    private SysXssBlacklistRule rule(Long tenantId) {
        var rule = new SysXssBlacklistRule();
        rule.setId(901L);
        rule.setTenantId(tenantId);
        rule.setRuleName("original");
        rule.setRuleType("CUSTOM_PATTERN");
        rule.setPattern("bad");
        rule.setEnabled(1);
        return rule;
    }
}
