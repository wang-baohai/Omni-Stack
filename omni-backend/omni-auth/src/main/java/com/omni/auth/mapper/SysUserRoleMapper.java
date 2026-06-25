package com.omni.auth.mapper;

import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

/**
 * 用户角色关联表 Mapper 接口。
 * <p>操作 {@code sys_user_role} 关联表，管理用户与角色的多对多关系。
 * 支持单条插入、批量删除、指定删除和查询用户角色列表。</p>
 *
 * @author Omni-Stack Team
 * @see SysUserMapper
 * @see SysRoleMapper
 */
public interface SysUserRoleMapper {

    /**
     * 插入用户角色关联。
     *
     * @param userId 用户 ID
     * @param roleId 角色 ID
     */
    @Insert("INSERT INTO sys_user_role (user_id, role_id) VALUES (#{userId}, #{roleId})")
    void insert(@Param("userId") Long userId, @Param("roleId") Long roleId);

    /**
     * 删除用户的所有角色关联。
     *
     * @param userId 用户 ID
     */
    @Delete("DELETE FROM sys_user_role WHERE user_id = #{userId}")
    void deleteByUserId(@Param("userId") Long userId);

    /**
     * 删除指定的用户角色关联。
     *
     * @param userId 用户 ID
     * @param roleId 角色 ID
     */
    @Delete("DELETE FROM sys_user_role WHERE user_id = #{userId} AND role_id = #{roleId}")
    void deleteByUserAndRole(@Param("userId") Long userId, @Param("roleId") Long roleId);

    /**
     * 查询用户拥有的所有角色 ID。
     *
     * @param userId 用户 ID
     * @return 角色 ID 列表
     */
    @Select("SELECT role_id FROM sys_user_role WHERE user_id = #{userId}")
    List<Long> selectRoleIdsByUserId(@Param("userId") Long userId);
}
