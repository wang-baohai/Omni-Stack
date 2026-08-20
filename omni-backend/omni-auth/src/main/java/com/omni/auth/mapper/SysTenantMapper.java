package com.omni.auth.mapper;

import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.omni.auth.entity.SysTenant;

/**
 * 系统租户 Mapper 接口。
 * <p>提供 {@code sys_tenant} 表的 CRUD 操作，
 * 租户查询主要用于登录页租户选择器和租户管理界面。</p>
 *
 * @author Omni-Stack Team
 * @see com.omni.auth.entity.SysTenant
 */
public interface SysTenantMapper extends BaseMapper<SysTenant> {

    /**
     * 锁定租户行，串行化模块结果汇总和重试。
     *
     * @param id 租户 ID
     * @return 被锁定的租户，不存在时返回 null
     */
    @Select("SELECT * FROM sys_tenant WHERE id = #{id} FOR UPDATE")
    SysTenant selectByIdForUpdate(@Param("id") Long id);
}
