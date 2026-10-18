package com.omni.auth.service.impl;

import com.omni.auth.entity.SysUser;
import com.omni.auth.event.AuditEvent;
import com.omni.auth.mapper.SysUserMapper;
import com.omni.auth.security.DataScopeContext;
import com.omni.auth.service.OnlineUserVO;
import com.omni.common.core.result.BusinessException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.util.List;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.catchThrowable;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * 在线用户管理的租户、组织范围和会话副作用回归测试。
 */
@ExtendWith(MockitoExtension.class)
class OnlineUserServiceImplTest {

    @Mock private StringRedisTemplate redis;
    @Mock private ValueOperations<String, String> values;
    @Mock private SysUserMapper userMapper;
    @Mock private ApplicationEventPublisher events;

    /** 清理请求数据范围，防止测试间泄漏。 */
    @AfterEach
    void clearScope() {
        DataScopeContext.clear();
    }

    /** TENANT 列表不得暴露全局 Redis 中其他租户的会话。 */
    @Test
    void shouldExcludeOtherTenantOnlineUsers() {
        DataScopeContext.set(scope("TENANT"));
        stubSessions(Set.of(7L, 9L), List.of(user(7L, 2L, 10L), user(9L, 3L, 10L)));

        List<OnlineUserVO> result = service().listOnlineUsers();

        assertThat(result).extracting(OnlineUserVO::getUserId).containsExactly(7L);
        assertThat(result.getFirst().getUsername()).isEqualTo("user-7");
        assertThat(result.getFirst().getJti()).isEqualTo("token-7");
    }

    /** 跨租户踢出必须在访问 Redis 或发布审计事件之前拒绝。 */
    @Test
    void shouldRejectOtherTenantKickBeforeSessionSideEffects() {
        DataScopeContext.set(scope("TENANT"));
        when(userMapper.selectById(9L)).thenReturn(user(9L, 3L, 10L));

        Throwable failure = catchThrowable(() -> service().kickUser(9L, "operator", "127.0.0.1"));

        assertAll(
                () -> assertThat(failure).isInstanceOfSatisfying(BusinessException.class,
                        error -> assertThat(error.getCode()).isEqualTo(403)),
                () -> verifyNoInteractions(redis, values),
                () -> verifyNoInteractions(events));
    }

    /** ALL 保留跨租户可见性，其余范围按本租户和主组织或本人收窄。 */
    @ParameterizedTest
    @MethodSource("visibleSessions")
    void shouldListOnlyVisibleSessions(String effectiveScope, List<Long> expectedIds) {
        DataScopeContext.set(scope(effectiveScope));
        stubSessions(Set.of(7L, 8L, 9L),
                List.of(user(7L, 2L, 10L), user(8L, 2L, 11L), user(9L, 3L, 10L)));

        List<OnlineUserVO> result = service().listOnlineUsers();

        assertThat(result).extracting(OnlineUserVO::getUserId)
                .containsExactlyInAnyOrderElementsOf(expectedIds);
        assertThat(result).filteredOn(item -> item.getUserId().equals(7L))
                .singleElement().satisfies(item -> assertThat(item.getPrimaryUnitId()).isEqualTo(10L));
        verifyNoInteractions(events);
    }

    /** 创建各数据范围的预期可见用户矩阵。 */
    private static Stream<Arguments> visibleSessions() {
        return Stream.of(
                Arguments.of("ALL", List.of(7L, 8L, 9L)),
                Arguments.of("TENANT", List.of(7L, 8L)),
                Arguments.of("DEPT", List.of(7L)),
                Arguments.of("DEPT_AND_BELOW", List.of(7L)),
                Arguments.of("CUSTOM", List.of(7L)),
                Arguments.of("SELF", List.of(7L)));
    }

    /** 已删除用户的残留 Redis 会话不能成为可见用户。 */
    @Test
    void shouldDropOrphanOnlineEntries() {
        DataScopeContext.set(scope("ALL"));
        stubSessions(Set.of(7L, 404L), List.of(user(7L, 2L, 10L)));

        assertThat(service().listOnlineUsers()).extracting(OnlineUserVO::getUserId).containsExactly(7L);
    }

    /** 组织或本人范围外的目标必须拒绝，其他租户的相同组织 ID 也不能放行。 */
    @ParameterizedTest
    @CsvSource({
            "DEPT,8,2,11",
            "DEPT_AND_BELOW,8,2,11",
            "CUSTOM,8,2,11",
            "DEPT,9,3,10",
            "DEPT_AND_BELOW,9,3,10",
            "CUSTOM,9,3,10",
            "SELF,8,2,10"
    })
    void shouldRejectTargetsOutsideDataScope(String effectiveScope, long targetId,
                                             long tenantId, long unitId) {
        DataScopeContext.set(scope(effectiveScope));
        when(userMapper.selectById(targetId)).thenReturn(user(targetId, tenantId, unitId));

        assertForbidden(() -> service().kickUser(targetId, "operator", "127.0.0.1"));

        verifyNoInteractions(redis, values, events);
    }

    /** 授权目标保留会话删除、默认黑名单期限和目标租户审计。 */
    @ParameterizedTest
    @CsvSource({
            "ALL,9,3,10",
            "TENANT,8,2,11",
            "DEPT,7,2,10",
            "DEPT_AND_BELOW,7,2,10",
            "CUSTOM,7,2,10",
            "SELF,7,2,10"
    })
    void shouldKickVisibleTargets(String effectiveScope, long targetId, long tenantId, long unitId) {
        DataScopeContext.set(scope(effectiveScope));
        when(userMapper.selectById(targetId)).thenReturn(user(targetId, tenantId, unitId));
        when(redis.opsForValue()).thenReturn(values);
        when(values.get("online:" + targetId)).thenReturn("user-" + targetId + "|token-" + targetId);

        service().kickUser(targetId, "operator", "127.0.0.1");

        verify(redis).delete("online:" + targetId);
        verify(values).set("token:blacklist:token-" + targetId, "1", 24, TimeUnit.HOURS);
        ArgumentCaptor<AuditEvent> audit = ArgumentCaptor.forClass(AuditEvent.class);
        verify(events).publishEvent(audit.capture());
        assertThat(audit.getValue().getTenantId()).isEqualTo(tenantId);
        assertThat(audit.getValue().getUserId()).isEqualTo(targetId);
        assertThat(audit.getValue().getEventType()).isEqualTo(AuditEvent.LOGOUT);
        assertThat(audit.getValue().getExtra()).containsEntry("method", "admin_kick");
    }

    /** 空组织集合不能扩大为全部可见。 */
    @Test
    void shouldHideSessionsWithoutOrganizationScope() {
        DataScopeContext.DataScopeInfo scope = scope("DEPT");
        scope.setAccessibleUnitIds(Set.of());
        DataScopeContext.set(scope);
        stubSessions(Set.of(7L, 9L), List.of(user(7L, 2L, 10L), user(9L, 3L, 10L)));

        assertThat(service().listOnlineUsers()).isEmpty();
    }

    /** 空组织集合不能允许踢出。 */
    @Test
    void shouldDenyEmptyOrganizationScope() {
        DataScopeContext.DataScopeInfo scope = scope("DEPT");
        scope.setAccessibleUnitIds(Set.of());
        DataScopeContext.set(scope);
        when(userMapper.selectById(7L)).thenReturn(user(7L, 2L, 10L));

        assertForbidden(() -> service().kickUser(7L, "operator", "127.0.0.1"));

        verifyNoInteractions(redis, values, events);
    }

    /** 缺失目标不能先删除在线键，即使操作者具有 ALL 范围。 */
    @Test
    void shouldRejectMissingTargetBeforeSessionSideEffects() {
        DataScopeContext.set(scope("ALL"));

        assertForbidden(() -> service().kickUser(404L, "operator", "127.0.0.1"));

        verifyNoInteractions(redis, values, events);
    }

    /** 缺失或无效请求范围必须在任何数据访问之前失败关闭。 */
    @ParameterizedTest
    @ValueSource(strings = {"MISSING_CONTEXT", "UNKNOWN_SCOPE", "NULL_SCOPE", "MISSING_TENANT", "MISSING_USER"})
    void shouldFailClosedWithoutValidScope(String invalidCase) {
        DataScopeContext.DataScopeInfo scope = scope("TENANT");
        switch (invalidCase) {
            case "MISSING_CONTEXT" -> scope = null;
            case "UNKNOWN_SCOPE" -> scope.setEffectiveScope("UNKNOWN");
            case "NULL_SCOPE" -> scope.setEffectiveScope(null);
            case "MISSING_TENANT" -> scope.setTenantId(null);
            case "MISSING_USER" -> scope.setUserId(null);
            default -> throw new IllegalArgumentException(invalidCase);
        }
        DataScopeContext.set(scope);

        assertForbidden(() -> service().listOnlineUsers());
        assertForbidden(() -> service().kickUser(7L, "operator", "127.0.0.1"));

        verifyNoInteractions(redis, values, userMapper, events);
    }

    /** 断言返回权限业务错误。 */
    private void assertForbidden(Runnable action) {
        assertThatThrownBy(action::run).isInstanceOfSatisfying(BusinessException.class,
                error -> assertThat(error.getCode()).isEqualTo(403));
    }

    /** 创建包含其他租户和残留键的受控在线会话依赖。 */
    private void stubSessions(Set<Long> onlineIds, List<SysUser> users) {
        when(redis.keys("online:*")).thenReturn(onlineIds.stream()
                .map(id -> "online:" + id).collect(Collectors.toSet()));
        when(redis.opsForValue()).thenReturn(values);
        when(values.get(anyString())).thenAnswer(invocation -> {
            String id = invocation.<String>getArgument(0).substring("online:".length());
            return "user-" + id + "|token-" + id;
        });
        when(userMapper.selectList(any())).thenReturn(users);
    }

    /** 创建当前租户和组织的数据范围。 */
    private DataScopeContext.DataScopeInfo scope(String effectiveScope) {
        DataScopeContext.DataScopeInfo scope = new DataScopeContext.DataScopeInfo();
        scope.setUserId(7L);
        scope.setTenantId(2L);
        scope.setPrimaryUnitId(10L);
        scope.setEffectiveScope(effectiveScope);
        scope.setAccessibleUnitIds(Set.of(10L));
        return scope;
    }

    /** 创建带有明确租户和主组织的用户。 */
    private SysUser user(Long id, Long tenantId, Long unitId) {
        SysUser user = new SysUser();
        user.setId(id);
        user.setTenantId(tenantId);
        user.setUsername("user-" + id);
        user.setPrimaryUnitId(unitId);
        return user;
    }

    /** 创建使用受控依赖的服务。 */
    private OnlineUserServiceImpl service() {
        return new OnlineUserServiceImpl(redis, userMapper, events);
    }
}
