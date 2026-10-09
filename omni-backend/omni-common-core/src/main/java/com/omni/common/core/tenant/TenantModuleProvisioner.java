package com.omni.common.core.tenant;

import com.omni.common.core.tenant.TenantProvisionContracts.ProvisionRequestedEvent;

/**
 * 单个服务的租户模块初始化扩展点。
 *
 * <p>实现类只负责本服务数据库内的领域默认数据，并且必须支持相同业务意图的重复执行。</p>
 */
public interface TenantModuleProvisioner {

    /**
     * 返回与模块目录一致的稳定模块 ID。
     *
     * @return 模块 ID
     */
    String moduleId();

    /**
     * 幂等初始化当前模块。
     *
     * @param event 不含管理员凭据的租户初始化请求
     */
    void provision(ProvisionRequestedEvent event);
}
