package com.omni.auth.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.omni.auth.entity.SysUser;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

/**
 * 系统用户 Mapper 接口。
 * <p>提供 {@code sys_user} 表的 CRUD 操作，以及用户角色编码、权限编码的关联查询。
 * 角色和权限查询结果用于 JWT Token 的 claims 填充。</p>
 *
 * @author Omni-Stack Team
 * @see com.omni.auth.entity.SysUser
 * @see SysUserRoleMapper
 */
public interface SysUserMapper extends BaseMapper<SysUser> {

    /**
     * 根据用户 ID 和租户 ID 查询用户。
     *
     * @param id       用户 ID
     * @param tenantId 租户 ID
     * @return 匹配的用户实体，不存在时返回 null
     */
    @Select("SELECT * FROM sys_user WHERE id = #{id} AND tenant_id = #{tenantId}")
    SysUser selectByIdAndTenantId(@Param("id") Long id, @Param("tenantId") Long tenantId);

    /**
     * 根据用户 ID 和租户 ID 查询启用用户。
     *
     * @param id       用户 ID
     * @param tenantId 租户 ID
     * @return 匹配的启用用户实体，不存在时返回 null
     */
    @Select("SELECT * FROM sys_user "
            + "WHERE id = #{id} AND tenant_id = #{tenantId} AND status = 1")
    SysUser selectEnabledByIdAndTenantId(@Param("id") Long id, @Param("tenantId") Long tenantId);

    /**
     * 在指定租户内批量查询用户。
     *
     * @param ids      用户 ID 列表
     * @param tenantId 租户 ID
     * @return 匹配的用户列表
     */
    @Select("<script>"
            + "SELECT * FROM sys_user WHERE tenant_id = #{tenantId} AND id IN "
            + "<foreach collection='ids' item='id' open='(' separator=',' close=')'>"
            + "#{id}"
            + "</foreach>"
            + "</script>")
    List<SysUser> selectByIdsAndTenantId(@Param("ids") List<Long> ids, @Param("tenantId") Long tenantId);

    /**
     * 搜索指定租户内的启用用户候选项。
     *
     * @param tenantId 租户 ID
     * @param keyword  用户名或昵称关键字，可为空
     * @param limit    最大返回数量
     * @return 启用用户列表
     */
    @Select("<script>"
            + "SELECT id, tenant_id, username, nickname, primary_unit_id, avatar, status "
            + "FROM sys_user WHERE tenant_id = #{tenantId} AND status = 1 "
            + "<if test='keyword != null and keyword != &quot;&quot;'>"
            + "AND (username LIKE CONCAT('%', #{keyword}, '%') "
            + "OR nickname LIKE CONCAT('%', #{keyword}, '%')) "
            + "</if>"
            + "ORDER BY username ASC, id ASC LIMIT #{limit}"
            + "</script>")
    List<SysUser> searchEnabledUsers(@Param("tenantId") Long tenantId,
                                     @Param("keyword") String keyword,
                                     @Param("limit") int limit);

    /**
     * 在指定租户内根据用户名查询用户。
     * <p>仅查询启用状态（status = 1）的用户。</p>
     *
     * @param username 用户名
     * @param tenantId 租户 ID
     * @return 匹配的用户实体，不存在时返回 null
     */
    @Select("SELECT * FROM sys_user WHERE tenant_id = #{tenantId} AND username = #{username} AND status = 1")
    SysUser selectByUsernameAndTenantId(@Param("username") String username, @Param("tenantId") Long tenantId);

    /**
     * 根据用户 ID 查询该用户拥有的所有角色编码。
     * <p>通过 {@code sys_user_role} 关联表 JOIN 查询，仅返回启用状态的角色。</p>
     *
     * @param userId 用户 ID
     * @return 角色编码列表
     */
    @Select("SELECT r.role_code FROM sys_role r "
            + "INNER JOIN sys_user_role ur ON r.id = ur.role_id "
            + "WHERE ur.user_id = #{userId} AND r.status = 1")
    List<String> selectRoleCodesByUserId(@Param("userId") Long userId);

    /**
     * 根据用户 ID 查询该用户拥有的所有权限编码（去重）。
     * <p>
     * 通过 {@code sys_user_role} -> {@code sys_role_permission} -> {@code sys_permission}
     * 三表关联查询，仅返回启用状态的权限。
     * </p>
     *
     * @param userId 用户 ID
     * @return 权限编码列表（已去重）
     */
    @Select("SELECT DISTINCT p.permission_code FROM sys_permission p "
            + "INNER JOIN sys_role_permission rp ON p.id = rp.permission_id "
            + "INNER JOIN sys_user_role ur ON rp.role_id = ur.role_id "
            + "WHERE ur.user_id = #{userId} AND p.status = 1")
    List<String> selectPermissionsByUserId(@Param("userId") Long userId);
}
