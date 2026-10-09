package com.omni.common.service.datascope;

import com.omni.common.core.internal.InternalDataScopeDTO;
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

class ServiceDataScopeAspectTest {

    @AfterEach
    void clearContexts() {
        ServiceIdentityContext.clear();
        ServiceDataScopeContext.clear();
    }

    @Test
    void shouldBindAuthoritativeScopeAndClearIt() throws Throwable {
        ServiceRequestIdentity identity = new ServiceRequestIdentity(7L, 3L, "alice");
        ServiceIdentityContext.set(identity);
        InternalDataScopeDTO dto = scope(7L, 3L, "crm:lead:list");
        ServiceDataScopeAspect aspect = new ServiceDataScopeAspect((requestIdentity, permissionCode) -> dto);
        ProceedingJoinPoint joinPoint = mock(ProceedingJoinPoint.class);
        when(joinPoint.proceed()).thenAnswer(invocation -> {
            assertThat(ServiceDataScopeContext.require().accessibleUnitIds()).containsExactlyInAnyOrder(11L, 12L);
            return "ok";
        });
        ServiceDataScope annotation = annotation("crm:lead:list");

        assertThat(aspect.bindScope(joinPoint, annotation)).isEqualTo("ok");
        assertThat(ServiceDataScopeContext.get()).isNull();
    }

    @Test
    void shouldRejectMismatchedTenantAndStillClearContext() {
        ServiceIdentityContext.set(new ServiceRequestIdentity(7L, 3L, "alice"));
        ServiceDataScopeAspect aspect = new ServiceDataScopeAspect(
                (identity, permissionCode) -> scope(7L, 99L, permissionCode));

        assertThatThrownBy(() -> aspect.bindScope(mock(ProceedingJoinPoint.class), annotation("crm:lead:list")))
                .hasMessageContaining("不一致的数据范围");
        assertThat(ServiceDataScopeContext.get()).isNull();
    }

    private InternalDataScopeDTO scope(Long userId, Long tenantId, String permissionCode) {
        InternalDataScopeDTO dto = new InternalDataScopeDTO();
        dto.setUserId(userId);
        dto.setTenantId(tenantId);
        dto.setPermissionCode(permissionCode);
        dto.setEffectiveScope("DEPT");
        dto.setAccessibleUnitIds(Set.of(11L, 12L));
        dto.setSecurityVersion(5L);
        return dto;
    }

    private ServiceDataScope annotation(String permissionCode) {
        return new ServiceDataScope() {
            @Override
            public String permissionCode() {
                return permissionCode;
            }

            @Override
            public Class<ServiceDataScope> annotationType() {
                return ServiceDataScope.class;
            }
        };
    }
}
