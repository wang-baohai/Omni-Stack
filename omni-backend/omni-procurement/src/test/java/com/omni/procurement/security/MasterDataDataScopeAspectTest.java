package com.omni.procurement.security;

import com.omni.common.core.internal.InternalDataScopeDTO;
import com.omni.common.core.result.R;
import com.omni.procurement.client.AuthInternalClient;
import org.aspectj.lang.ProceedingJoinPoint;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/** 主数据请求的数据范围 ThreadLocal 清理测试。 */
class MasterDataDataScopeAspectTest {

    /** 清理租户和数据范围上下文。 */
    @AfterEach
    void clearContexts() {
        ProcDataScopeContext.clear();
        ProcTenantContext.clear();
    }

    /** 业务方法异常时也必须在 finally 清除数据范围。 */
    @Test
    void shouldClearDataScopeWhenBusinessMethodFails() throws Throwable {
        ProcTenantContext.set(new ProcTenantContext.RequestIdentity(7L, 51L, "buyer"));
        AuthInternalClient client = mock(AuthInternalClient.class);
        ProcDataScope annotation = mock(ProcDataScope.class);
        ProceedingJoinPoint joinPoint = mock(ProceedingJoinPoint.class);
        when(annotation.permissionCode()).thenReturn("procurement:material:list");
        when(client.resolveDataScope(7L, 51L, "procurement:material:list"))
                .thenReturn(R.ok(scope()));
        when(joinPoint.proceed()).thenThrow(new IllegalStateException("business failed"));

        ProcDataScopeAspect aspect = new ProcDataScopeAspect(client);
        assertThatThrownBy(() -> aspect.bindScope(joinPoint, annotation))
                .isInstanceOf(IllegalStateException.class);

        assertThat(ProcDataScopeContext.get()).isNull();
    }

    private InternalDataScopeDTO scope() {
        InternalDataScopeDTO scope = new InternalDataScopeDTO();
        scope.setUserId(7L);
        scope.setTenantId(51L);
        scope.setPermissionCode("procurement:material:list");
        scope.setPrimaryUnitId(3L);
        scope.setEffectiveScope("TENANT");
        scope.setAccessibleUnitIds(Set.of(3L));
        return scope;
    }
}
