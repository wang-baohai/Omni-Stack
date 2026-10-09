package com.omni.crm.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;

import java.time.Instant;
import java.util.List;

import org.junit.jupiter.api.Test;

import com.omni.common.core.result.BusinessException;
import com.omni.common.core.tenant.TenantProvisionContracts.ProvisionRequestedEvent;
import com.omni.common.service.identity.ServiceIdentityContext;
import com.omni.crm.service.CrmTenantInitializer;

/**
 * CRM 租户模块上下文适配测试。
 */
class CrmTenantModuleProvisionerTest {

    /** 初始化期间必须使用目标租户，并在完成后清理线程上下文。 */
    @Test
    void shouldUseTargetTenantAndClearContext() {
        CrmTenantInitializer initializer = mock(CrmTenantInitializer.class);
        doAnswer(invocation -> {
            assertThat(ServiceIdentityContext.requireTenantId()).isEqualTo(9L);
            return 99L;
        }).when(initializer).ensureInitialized();

        new CrmTenantModuleProvisioner(initializer).provision(request());

        assertThatThrownBy(ServiceIdentityContext::requireTenantId)
                .isInstanceOf(BusinessException.class)
                .satisfies(error -> assertThat(((BusinessException) error).getCode()).isEqualTo(403));
    }

    private static ProvisionRequestedEvent request() {
        return new ProvisionRequestedEvent(
                "event-1", "request-1", 9L, "tenant-9", "租户 9", List.of("crm"), Instant.now());
    }
}
