package com.omni.auth.service;

/**
 * Auth 所拥有租户数据的本地幂等初始化器。
 */
public interface TenantLocalProvisioner {

    /**
     * 从默认租户模板初始化权限、角色、组织、管理员和 XSS。
     *
     * @param tenantId             目标租户 ID
     * @param tenantName           目标租户名称
     * @param encodedAdminPassword 管理员 BCrypt 哈希，仅用于首次创建管理员
     */
    void provisionLocal(Long tenantId, String tenantName, String encodedAdminPassword);
}
