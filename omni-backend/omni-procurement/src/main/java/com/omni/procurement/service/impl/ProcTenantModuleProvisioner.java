package com.omni.procurement.service.impl;

import org.springframework.stereotype.Component;

import com.omni.common.core.tenant.TenantModuleProvisioner;
import com.omni.common.core.tenant.TenantProvisionContracts.ProvisionRequestedEvent;
import com.omni.common.service.identity.ServiceIdentityContext;
import com.omni.common.service.identity.ServiceRequestIdentity;
import com.omni.procurement.service.ProcTenantInitializer;
import lombok.RequiredArgsConstructor;

/**
 * 采购租户模块事件初始化适配器。
 */
@Component
@RequiredArgsConstructor
public class ProcTenantModuleProvisioner implements TenantModuleProvisioner {

    private final ProcTenantInitializer tenantInitializer;

    /** {@inheritDoc} */
    @Override
    public String moduleId() {
        return "procurement";
    }

    /** {@inheritDoc} */
    @Override
    public void provision(ProvisionRequestedEvent event) {
        ServiceIdentityContext.set(new ServiceRequestIdentity(
                0L, event.tenantId(), "tenant-provisioning"));
        try {
            tenantInitializer.ensureInitialized();
        } finally {
            ServiceIdentityContext.clear();
        }
    }
}
