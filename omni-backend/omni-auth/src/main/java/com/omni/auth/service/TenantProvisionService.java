package com.omni.auth.service;

import java.util.List;

import com.omni.auth.entity.SysTenant;
import com.omni.auth.entity.SysTenantModuleProvision;
import com.omni.common.core.tenant.TenantProvisionContracts.ProvisionResultEvent;

/**
 * 租户模块化初始化协调服务。
 */
public interface TenantProvisionService {

    /**
     * 启动新租户初始化，在同一事务完成 Auth 本地数据和请求 Outbox。
     *
     * @param tenant               已持久化的目标租户
     * @param encodedAdminPassword 管理员 BCrypt 哈希
     */
    void startProvisioning(SysTenant tenant, String encodedAdminPassword);

    /**
     * 汇总单个模块的初始化结果。
     *
     * @param event 模块结果事件
     */
    void handleResult(ProvisionResultEvent event);

    /**
     * 重试失败模块，已成功模块保持成功状态。
     *
     * @param tenantId 租户 ID
     */
    void retryFailedModules(Long tenantId);

    /**
     * 查询租户各模块的初始化状态。
     *
     * @param tenantId 租户 ID
     * @return 按记录 ID 排序的模块状态
     */
    List<SysTenantModuleProvision> listModuleStates(Long tenantId);
}
