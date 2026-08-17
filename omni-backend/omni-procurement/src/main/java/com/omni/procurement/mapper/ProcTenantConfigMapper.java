package com.omni.procurement.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.annotation.InterceptorIgnore;
import com.omni.procurement.entity.ProcTenantConfig;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

/**
 * 采购租户配置 Mapper。
 *
 * @author Omni-Stack Team
 */
public interface ProcTenantConfigMapper extends BaseMapper<ProcTenantConfig> {

    /**
     * 锁定并读取租户当前有效配置，保证并发初始化使用当前读。
     *
     * @param tenantId 租户 ID
     * @return 租户配置，不存在时返回 null
     */
    @InterceptorIgnore(tenantLine = "true", dataPermission = "true")
    @Select("""
            SELECT *
            FROM proc_tenant_config
            WHERE tenant_id = #{tenantId}
              AND deleted = 0
            LIMIT 1
            FOR UPDATE
            """)
    ProcTenantConfig selectForUpdateByTenant(@Param("tenantId") Long tenantId);
}
