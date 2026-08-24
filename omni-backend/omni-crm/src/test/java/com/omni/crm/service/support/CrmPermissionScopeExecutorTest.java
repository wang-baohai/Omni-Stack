package com.omni.crm.service.support;

import com.omni.common.core.internal.InternalDataScopeDTO;
import com.omni.common.core.result.BusinessException;
import com.omni.common.core.result.R;
import com.omni.crm.client.AuthInternalClient;
import com.omni.common.service.datascope.ServiceDataScopeContext;
import com.omni.common.service.identity.ServiceIdentityContext;
import com.omni.common.service.identity.ServiceRequestIdentity;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/** 跨聚合精确权限范围执行器测试。 */
class CrmPermissionScopeExecutorTest {

    /** 清理线程上下文。 */
    @AfterEach
    void clear() {
        ServiceDataScopeContext.clear(); ServiceIdentityContext.clear(); SecurityContextHolder.clearContext();
    }

    /** Auth 返回错租户 scope 时必须拒绝，且不得执行查询。 */
    @Test
    void shouldRejectMismatchedAuthoritativeScope() {
        AuthInternalClient client = mock(AuthInternalClient.class);
        CrmPermissionScopeExecutor executor = new CrmPermissionScopeExecutor(client);
        ServiceIdentityContext.set(new ServiceRequestIdentity(12L, 3L, "sales"));
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(
                "sales", null, List.of(new SimpleGrantedAuthority("crm:contact:list"))));
        InternalDataScopeDTO scope = new InternalDataScopeDTO(); scope.setUserId(12L); scope.setTenantId(99L);
        scope.setPermissionCode("crm:contact:list"); scope.setEffectiveScope("TENANT"); scope.setAccessibleUnitIds(Set.of());
        when(client.resolveDataScope(12L, 3L, "crm:contact:list")).thenReturn(R.ok(scope));
        AtomicBoolean executed = new AtomicBoolean();

        assertThatThrownBy(() -> executor.executeIfGranted("crm:contact:list", () -> {
            executed.set(true); return List.of(1L);
        }, List.of())).isInstanceOf(BusinessException.class)
                .satisfies(error -> assertThat(((BusinessException) error).getCode()).isEqualTo(403));
        assertThat(executed).isFalse();
    }
}
