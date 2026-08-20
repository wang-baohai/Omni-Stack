package com.omni.crm.service.impl;

import org.springframework.stereotype.Component;

import com.omni.common.core.tenant.TenantModuleProvisioner;
import com.omni.common.core.tenant.TenantProvisionContracts.ProvisionRequestedEvent;
import com.omni.crm.security.CrmTenantContext;
import com.omni.crm.service.CrmTenantInitializer;
import lombok.RequiredArgsConstructor;

/**
 * CRM 租户模块事件初始化适配器。
 */
@Component
@RequiredArgsConstructor
public class CrmTenantModuleProvisioner implements TenantModuleProvisioner {

    private final CrmTenantInitializer tenantInitializer;

    /** {@inheritDoc} */
    @Override
    public String moduleId() {
        return "crm";
    }

    /** {@inheritDoc} */
    @Override
    public void provision(ProvisionRequestedEvent event) {
        CrmTenantContext.set(new CrmTenantContext.RequestIdentity(
                0L, event.tenantId(), "tenant-provisioning"));
        try {
            tenantInitializer.ensureInitialized();
        } finally {
            CrmTenantContext.clear();
        }
    }
}
