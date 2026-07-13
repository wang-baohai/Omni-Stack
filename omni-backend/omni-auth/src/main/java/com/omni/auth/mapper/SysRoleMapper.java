package com.omni.auth.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.omni.auth.entity.SysRole;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

/**
 * 系统角色 Mapper 接口。
 * <p>提供 {@code sys_role} 表的 CRUD 操作，以及通过关联表查询用户角色的方法。</p>
 *
 * @author Omni-Stack Team
 * @see com.omni.auth.entity.SysRole
 * @see SysUserRoleMapper
 */
public interface SysRoleMapper extends BaseMapper<SysRole> {

    /**
     * 根据用户 ID 和租户 ID 查询启用角色。
     *
     * @param userId   用户 ID
     * @param tenantId 租户 ID
     * @return 用户在指定租户内拥有的启用角色
     */
    @Select("SELECT r.* FROM sys_role r "
            + "INNER JOIN sys_user_role ur ON r.id = ur.role_id "
            + "WHERE ur.user_id = #{userId} AND r.tenant_id = #{tenantId} AND r.status = 1")
    List<SysRole> selectRolesByUserIdAndTenantId(@Param("userId") Long userId,
                                                 @Param("tenantId") Long tenantId);

    /**
     * 查询真正授予指定权限的用户角色。
     * <p>权限码使用精确匹配，并同时约束角色和权限所属租户，防止跨角色权限拼接。</p>
     *
     * @param userId         用户 ID
     * @param tenantId       租户 ID
     * @param permissionCode 完整权限码
     * @return 授予该权限的启用角色列表
     */
    @Select("SELECT DISTINCT r.* FROM sys_role r "
            + "INNER JOIN sys_user_role ur ON r.id = ur.role_id "
            + "INNER JOIN sys_role_permission rp ON r.id = rp.role_id "
            + "INNER JOIN sys_permission p ON rp.permission_id = p.id "
            + "WHERE ur.user_id = #{userId} "
            + "AND r.tenant_id = #{tenantId} AND p.tenant_id = #{tenantId} "
            + "AND r.status = 1 AND p.status = 1 "
            + "AND p.permission_code = #{permissionCode}")
    List<SysRole> selectRolesGrantingPermission(@Param("userId") Long userId,
                                                @Param("tenantId") Long tenantId,
                                                @Param("permissionCode") String permissionCode);

    /**
     * 根据用户 ID 查询该用户拥有的所有角色。
     * <p>通过 {@code sys_user_role} 关联表进行 JOIN 查询，仅返回启用状态的角色。</p>
     *
     * @param userId 用户 ID
     * @return 用户角色列表
     */
    @Select("SELECT r.* FROM sys_role r "
            + "INNER JOIN sys_user_role ur ON r.id = ur.role_id "
            + "WHERE ur.user_id = #{userId} AND r.status = 1")
    List<SysRole> selectRolesByUserId(@Param("userId") Long userId);

    /**
     * 根据租户 ID 和角色编码查询启用状态的角色。
     *
     * @param tenantId 租户 ID
     * @param roleCode 角色编码
     * @return 匹配的角色实体，不存在时返回 null
     */
    @Select("SELECT * FROM sys_role WHERE tenant_id = #{tenantId} AND role_code = #{roleCode} AND status = 1")
    SysRole selectByTenantIdAndRoleCode(@Param("tenantId") Long tenantId, @Param("roleCode") String roleCode);
}
