package com.omni.asset.service.impl;

import org.springframework.stereotype.Component;

import com.omni.common.core.tenant.TenantModuleProvisioner;
import com.omni.common.core.tenant.TenantProvisionContracts.ProvisionRequestedEvent;

/**
 * Asset 租户模块初始化器。
 *
 * <p>Asset 数据库没有租户级默认业务事实；角色和权限由 Auth 初始化，资产字典由 Base 初始化。
 * 本实现用于显式确认该边界并参与统一可靠回执。</p>
 */
@Component
public class AssetTenantModuleProvisioner implements TenantModuleProvisioner {

    /** {@inheritDoc} */
    @Override
    public String moduleId() {
        return "asset";
    }

    /** {@inheritDoc} */
    @Override
    public void provision(ProvisionRequestedEvent event) {
        if (event.tenantId() == null || event.tenantId() <= 0) {
            throw new IllegalArgumentException("Asset 租户初始化 tenantId 无效");
        }
    }
}
