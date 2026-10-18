package com.omni.base.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.omni.base.entity.SysDictData;
import com.omni.base.entity.SysDictType;
import com.omni.base.mapper.SysDictDataMapper;
import com.omni.base.mapper.SysDictTypeMapper;
import com.omni.base.service.impl.DictDataServiceImpl;
import com.omni.base.service.impl.DictTypeServiceImpl;
import com.omni.common.result.GlobalExceptionHandler;
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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 字典 ID 资源的租户隔离回归测试。
 * <p>使用真实控制器和服务，Mapper、Redis 为内存替身，不代表网关或真实数据库验收。</p>
 */
@ExtendWith(MockitoExtension.class)
class DictTenantBoundaryTest {

    @Mock
    private SysDictTypeMapper typeMapper;

    @Mock
    private SysDictDataMapper dataMapper;

    @Mock
    private StringRedisTemplate redisTemplate;

    private MockMvc mvc;

    /** 初始化实际接口和服务，保留公共业务异常响应语义。 */
    @BeforeEach
    void setUp() {
        var typeService = new DictTypeServiceImpl(typeMapper, dataMapper, redisTemplate);
        var dataService = new DictDataServiceImpl(dataMapper, typeMapper, redisTemplate, new ObjectMapper());
        mvc = MockMvcBuilders.standaloneSetup(
                        new DictTypeController(typeService), new DictDataController(dataService))
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
        SecurityContextHolder.getContext().setAuthentication(
                new TestingAuthenticationToken("operator", null,
                        "dict:type:list", "dict:type:update", "dict:type:delete",
                        "dict:data:update", "dict:data:delete"));
    }

    /** 清理线程级认证信息。 */
    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    /** 有功能权限也不能读写另一租户的 ID 资源或删除其数据、缓存。 */
    @ParameterizedTest
    @ValueSource(strings = {"type-get", "type-update", "type-delete", "type-status", "data-update", "data-delete"})
    void should_reject_another_tenant_resource_without_writes(String operation) throws Exception {
        stubResource(operation, 9L);

        mvc.perform(request(operation).header("X-Tenant-Id", 8L))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(404));

        assertNoWrites();
    }

    /** 缺失与其他租户资源返回同一业务错误且无写入副作用。 */
    @ParameterizedTest
    @ValueSource(strings = {"type-get", "type-update", "type-delete", "type-status", "data-update", "data-delete"})
    void should_reject_missing_resource_without_writes(String operation) throws Exception {
        mvc.perform(request(operation).header("X-Tenant-Id", 8L))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(404));

        assertNoWrites();
    }

    /** 同租户保留原查询、写入、级联删除与缓存失效行为。 */
    @ParameterizedTest
    @ValueSource(strings = {"type-get", "type-update", "type-delete", "type-status", "data-update", "data-delete"})
    void should_keep_same_tenant_behavior(String operation) throws Exception {
        stubResource(operation, 8L);

        mvc.perform(request(operation).header("X-Tenant-Id", 8L))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));

        switch (operation) {
            case "type-get" -> verifyNoInteractions(redisTemplate);
            case "type-update", "type-status" -> verify(typeMapper).updateById(any(SysDictType.class));
            case "type-delete" -> {
                verify(dataMapper).delete(any());
                verify(typeMapper).deleteById(501L);
            }
            case "data-update" -> verify(dataMapper).updateById(any(SysDictData.class));
            case "data-delete" -> verify(dataMapper).deleteById(601L);
            default -> throw new IllegalArgumentException(operation);
        }
        if (!"type-get".equals(operation)) {
            verify(redisTemplate).delete("dict:type:8:demo");
        }
    }

    /** 验证被拒绝路径未写库、级联删除或访问缓存。 */
    private void assertNoWrites() {
        verify(typeMapper, never()).updateById(any(SysDictType.class));
        verify(typeMapper, never()).deleteById(anyLong());
        verify(dataMapper, never()).updateById(any(SysDictData.class));
        verify(dataMapper, never()).deleteById(anyLong());
        verify(dataMapper, never()).delete(any());
        verifyNoInteractions(redisTemplate);
    }

    /** 为单次测试提供只存在于内存的字典资源。 */
    private void stubResource(String operation, Long tenantId) {
        if (operation.startsWith("type-")) {
            var type = new SysDictType();
            type.setId(501L);
            type.setTenantId(tenantId);
            type.setTypeCode("demo");
            type.setTypeName("original");
            type.setStatus(1);
            when(typeMapper.selectById(501L)).thenReturn(type);
        } else {
            var data = new SysDictData();
            data.setId(601L);
            data.setTenantId(tenantId);
            data.setTypeCode("demo");
            data.setDictValue("a");
            data.setDictLabel("original");
            data.setStatus(1);
            when(dataMapper.selectById(601L)).thenReturn(data);
        }
    }

    /** 构造现有六个按 ID 访问的端点请求。 */
    private MockHttpServletRequestBuilder request(String operation) {
        return switch (operation) {
            case "type-get" -> get("/api/base/dict/type/501");
            case "type-update" -> put("/api/base/dict/type/501").contentType(MediaType.APPLICATION_JSON)
                    .content("{\"typeName\":\"renamed\"}");
            case "type-delete" -> delete("/api/base/dict/type/501");
            case "type-status" -> put("/api/base/dict/type/501/status").param("status", "0");
            case "data-update" -> put("/api/base/dict/data/601").contentType(MediaType.APPLICATION_JSON)
                    .content("{\"dictLabel\":\"renamed\"}");
            case "data-delete" -> delete("/api/base/dict/data/601");
            default -> throw new IllegalArgumentException(operation);
        };
    }
}
