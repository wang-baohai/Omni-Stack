package com.omni.auth.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.omni.auth.entity.SysPermission;

/**
 * 系统权限 Mapper 接口。
 * <p>提供 {@code sys_permission} 表的 CRUD 操作，
 * 权限编码通过 {@code SysUserMapper#selectPermissionsByUserId} 查询。</p>
 *
 * @author Omni-Stack Team
 * @see com.omni.auth.entity.SysPermission
 * @see SysUserMapper
 */
public interface SysPermissionMapper extends BaseMapper<SysPermission> {
}
