package com.omni.auth.mapper;

import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

/**
 * 角色权限关联表 Mapper 接口。
 * <p>操作 {@code sys_role_permission} 关联表，管理角色与权限的多对多关系。
 * 支持批量插入、批量删除和查询角色权限列表。</p>
 *
 * @author Omni-Stack Team
 * @see SysRoleMapper
 * @see SysPermissionMapper
 */
public interface SysRolePermissionMapper {

    /**
     * 批量插入角色权限关联。
     *
     * @param roleId        角色 ID
     * @param permissionIds 权限 ID 列表
     */
    @Insert("<script>"
            + "INSERT INTO sys_role_permission (role_id, permission_id) VALUES "
            + "<foreach collection='permissionIds' item='permId' separator=','>"
            + "(#{roleId}, #{permId})"
            + "</foreach>"
            + "</script>")
    void batchInsert(@Param("roleId") Long roleId, @Param("permissionIds") List<Long> permissionIds);

    /**
     * 删除角色的所有权限关联。
     *
     * @param roleId 角色 ID
     */
    @Delete("DELETE FROM sys_role_permission WHERE role_id = #{roleId}")
    void deleteByRoleId(@Param("roleId") Long roleId);

    /**
     * 查询角色拥有的所有权限 ID。
     *
     * @param roleId 角色 ID
     * @return 权限 ID 列表
     */
    @Select("SELECT permission_id FROM sys_role_permission WHERE role_id = #{roleId}")
    List<Long> selectPermissionIdsByRoleId(@Param("roleId") Long roleId);
}
