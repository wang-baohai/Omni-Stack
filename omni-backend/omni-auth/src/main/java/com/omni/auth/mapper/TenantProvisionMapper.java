package com.omni.auth.mapper;

import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

/**
 * 租户初始化 Mapper，调用 {@code sp_init_tenant} 存储过程。
 * <p>创建新租户后，通过此 Mapper 一键初始化租户的权限树、默认角色、
 * 根组织、管理员账号和 XSS 防护配置。</p>
 *
 * @author Omni-Stack Team
 * @see com.omni.auth.service.impl.TenantServiceImpl
 */
public interface TenantProvisionMapper {

    /**
     * 调用存储过程初始化租户数据。
     *
     * @param tenantId  新租户 ID
     * @param tenantName 租户名称（用于根组织命名）
     * @param adminPassword 管理员 BCrypt 密码
     */
    @Select("CALL sp_init_tenant(#{tenantId}, #{tenantName}, #{adminPassword})")
    void initTenant(@Param("tenantId") Long tenantId,
                    @Param("tenantName") String tenantName,
                    @Param("adminPassword") String adminPassword);
}
