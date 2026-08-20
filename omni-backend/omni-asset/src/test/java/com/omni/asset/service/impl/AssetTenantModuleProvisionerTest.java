package com.omni.asset.service.impl;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.util.List;

import org.junit.jupiter.api.Test;

import com.omni.common.core.tenant.TenantProvisionContracts.ProvisionRequestedEvent;

/**
 * Asset 空事实初始化边界测试。
 */
class AssetTenantModuleProvisionerTest {

    /** 合法租户应成功确认边界，非法租户必须失败关闭。 */
    @Test
    void shouldValidateTenantIdentity() {
        AssetTenantModuleProvisioner provisioner = new AssetTenantModuleProvisioner();

        assertThatCode(() -> provisioner.provision(request(9L))).doesNotThrowAnyException();
        assertThatThrownBy(() -> provisioner.provision(request(0L)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("tenantId");
    }

    private static ProvisionRequestedEvent request(Long tenantId) {
        return new ProvisionRequestedEvent(
                "event-1", "request-1", tenantId, "tenant", "租户", List.of("asset"), Instant.now());
    }
}
