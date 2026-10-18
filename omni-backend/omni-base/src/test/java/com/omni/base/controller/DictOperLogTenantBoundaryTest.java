package com.omni.base.controller;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.omni.base.dto.UpdateDictTypeRequest;
import com.omni.base.entity.SysDictType;
import com.omni.base.mapper.SysDictDataMapper;
import com.omni.base.mapper.SysDictTypeMapper;
import com.omni.base.service.impl.DictTypeServiceImpl;
import com.omni.common.core.operlog.OperLog;
import com.omni.common.core.operlog.OperLogMessage;
import com.omni.common.core.result.BusinessException;
import com.omni.common.operlog.aspect.OperLogAspect;
import com.omni.common.operlog.diff.EntityDiffer;
import com.omni.common.operlog.producer.OperLogProducer;
import com.omni.common.operlog.sanitize.OperLogSanitizer;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.reflect.MethodSignature;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationContext;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.lang.reflect.Method;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 字典业务拒绝跨租户请求时，操作日志快照也不得泄露其他租户的并发变化。
 * <p>直接执行真实切面、控制器和服务，数据、请求与生产者均为隔离内存替身。</p>
 */
@ExtendWith(MockitoExtension.class)
class DictOperLogTenantBoundaryTest {

    @Mock
    private SysDictTypeMapper typeMapper;
    @Mock
    private SysDictDataMapper dataMapper;
    @Mock
    private StringRedisTemplate redisTemplate;
    @Mock
    private OperLogProducer producer;
    @Mock
    private ApplicationContext applicationContext;
    @Mock
    private ProceedingJoinPoint joinPoint;
    @Mock
    private MethodSignature signature;

    private final ObjectMapper objectMapper = new ObjectMapper();
    private OperLogAspect aspect;
    private OperLog annotation;

    /** 绑定当前租户请求并连接实际字典更新方法。 */
    @BeforeEach
    void setUp() throws Throwable {
        var request = new MockHttpServletRequest("PUT", "/api/base/dict/type/501");
        request.addHeader("X-Tenant-Id", "8");
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));
        SecurityContextHolder.getContext().setAuthentication(
                new TestingAuthenticationToken("operator", null, "dict:type:update"));

        var update = new UpdateDictTypeRequest();
        update.setTypeName("updated");
        var controller = new DictTypeController(new DictTypeServiceImpl(typeMapper, dataMapper, redisTemplate));
        Method method = DictTypeController.class.getMethod(
                "update", Long.class, Long.class, UpdateDictTypeRequest.class);
        annotation = method.getAnnotation(OperLog.class);
        when(joinPoint.getSignature()).thenReturn(signature);
        when(signature.getMethod()).thenReturn(method);
        when(signature.getParameterNames()).thenReturn(new String[]{"id", "tenantId", "request"});
        when(joinPoint.getArgs()).thenReturn(new Object[]{501L, 8L, update});
        when(joinPoint.proceed()).thenAnswer(invocation -> controller.update(501L, 8L, update));
        when(applicationContext.getBeanNamesForType(BaseMapper.class)).thenReturn(new String[]{"typeMapper"});
        when(applicationContext.getBean("typeMapper")).thenReturn(typeMapper);
        aspect = new OperLogAspect(producer, new EntityDiffer(objectMapper), objectMapper,
                applicationContext, new OperLogSanitizer(objectMapper));
    }

    /** 清理请求和认证线程上下文。 */
    @AfterEach
    void tearDown() {
        RequestContextHolder.resetRequestAttributes();
        SecurityContextHolder.clearContext();
    }

    /** 其他租户在请求期间发生变化时，失败日志仍必须没有其快照。 */
    @Test
    void should_not_log_foreign_tenant_snapshots_when_dictionary_access_is_rejected() throws Throwable {
        var old = type(9L, "tenant9-original");
        var changedByOwner = type(9L, "tenant9-concurrent-change");
        when(typeMapper.selectById(501L)).thenReturn(old, old, changedByOwner);

        assertThatThrownBy(() -> aspect.around(joinPoint, annotation))
                .isInstanceOfSatisfying(BusinessException.class, ex -> assertThat(ex.getCode()).isEqualTo(404));

        OperLogMessage message = capturedMessage();
        assertThat(message.getTenantId()).isEqualTo(8L);
        assertThat(message.getOldValue()).isNull();
        assertThat(message.getNewValue()).isNull();
        verify(typeMapper, never()).updateById(any(SysDictType.class));
    }

    /** 同租户成功写入仍记录字段差异和原成功状态。 */
    @Test
    void should_keep_same_tenant_snapshot_diff_on_success() throws Throwable {
        var old = type(8L, "original");
        var updated = type(8L, "updated");
        when(typeMapper.selectById(501L)).thenReturn(old, old, updated);

        aspect.around(joinPoint, annotation);

        OperLogMessage message = capturedMessage();
        assertThat(message.getTenantId()).isEqualTo(8L);
        assertThat(message.getResponseStatus()).isEqualTo(200);
        assertThat(message.getOldValue()).isNotNull();
        assertThat(message.getNewValue()).isNotNull();
        assertThat(objectMapper.readTree(message.getOldValue()).has("typeName")).isTrue();
        assertThat(objectMapper.readTree(message.getNewValue()).has("typeName")).isTrue();
        verify(typeMapper).updateById(any(SysDictType.class));
    }

    /** 读取内存生产者捕获的消息，不发送真实 MQ。 */
    private OperLogMessage capturedMessage() {
        var captor = ArgumentCaptor.forClass(OperLogMessage.class);
        verify(producer).send(captor.capture());
        return captor.getValue();
    }

    /** 构造只在测试中存在的租户字典类型。 */
    private SysDictType type(Long tenantId, String name) {
        var entity = new SysDictType();
        entity.setId(501L);
        entity.setTenantId(tenantId);
        entity.setTypeCode("demo");
        entity.setTypeName(name);
        entity.setStatus(1);
        return entity;
    }
}
