package com.omni.auth.mapper;

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
}
