package com.omni.procurement.security;

import com.omni.common.core.internal.InternalDataScopeDTO;
import com.omni.common.service.datascope.ServiceDataScope;
import com.omni.common.service.datascope.ServiceDataScopeAspect;
import com.omni.common.service.datascope.ServiceDataScopeContext;
import com.omni.common.service.identity.ServiceIdentityContext;
import com.omni.common.service.identity.ServiceRequestIdentity;
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
        ServiceDataScopeContext.clear();
        ServiceIdentityContext.clear();
    }

    /** 业务方法异常时也必须在 finally 清除数据范围。 */
    @Test
    void shouldClearDataScopeWhenBusinessMethodFails() throws Throwable {
        ServiceIdentityContext.set(new ServiceRequestIdentity(7L, 51L, "buyer"));
        ServiceDataScope annotation = mock(ServiceDataScope.class);
        ProceedingJoinPoint joinPoint = mock(ProceedingJoinPoint.class);
        when(annotation.permissionCode()).thenReturn("procurement:material:list");
        when(joinPoint.proceed()).thenThrow(new IllegalStateException("business failed"));

        ServiceDataScopeAspect aspect = new ServiceDataScopeAspect((identity, permissionCode) -> scope());
        assertThatThrownBy(() -> aspect.bindScope(joinPoint, annotation))
                .isInstanceOf(IllegalStateException.class);

        assertThat(ServiceDataScopeContext.get()).isNull();
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
