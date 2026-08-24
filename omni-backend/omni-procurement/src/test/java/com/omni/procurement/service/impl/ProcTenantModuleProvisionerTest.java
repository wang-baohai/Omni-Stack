package com.omni.procurement.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;

import java.time.Instant;
import java.util.List;

import org.junit.jupiter.api.Test;

import com.omni.common.core.tenant.TenantProvisionContracts.ProvisionRequestedEvent;
import com.omni.common.service.identity.ServiceIdentityContext;
import com.omni.procurement.service.ProcTenantInitializer;

/**
 * 采购租户模块上下文适配测试。
 */
class ProcTenantModuleProvisionerTest {

    /** 初始化期间必须使用目标租户，并在完成后清理线程上下文。 */
    @Test
    void shouldUseTargetTenantAndClearContext() {
        ProcTenantInitializer initializer = mock(ProcTenantInitializer.class);
        doAnswer(invocation -> {
            assertThat(ServiceIdentityContext.requireTenantId()).isEqualTo(9L);
            return 99L;
        }).when(initializer).ensureInitialized();

        new ProcTenantModuleProvisioner(initializer).provision(request());

        assertThatThrownBy(ServiceIdentityContext::requireTenantId)
                .hasMessageContaining("缺少服务");
    }

    private static ProvisionRequestedEvent request() {
        return new ProvisionRequestedEvent(
                "event-1", "request-1", 9L, "tenant-9", "租户 9",
                List.of("procurement"), Instant.now());
    }
}
