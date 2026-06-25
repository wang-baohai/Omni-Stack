package com.omni.auth.service;

import com.omni.auth.dto.CreateRoleRequest;
import com.omni.auth.dto.UpdateRoleRequest;
import com.omni.auth.entity.SysRole;
import com.omni.common.core.result.PageResult;

import java.util.List;

/**
 * 角色服务接口，提供角色 CRUD、权限分配和部门分配操作。
 * <p>支持角色的完整生命周期管理，包括创建、查询、更新、删除，
 * 以及角色与权限、部门的关联操作。</p>
 *
 * @author Omni-Stack Team
 * @see PermissionService
 * @see com.omni.auth.entity.SysRole
 */
public interface RoleService {

    /**
     * 分页查询角色列表。
     *
     * @param tenantId 租户 ID
     * @param page     页码
     * @param size     每页大小
     * @return 角色分页结果
     */
    PageResult<SysRole> listRoles(Long tenantId, int page, int size);

    /**
     * 获取角色详情。
     *
     * @param id 角色 ID
     * @return 角色实体
     */
    SysRole getById(Long id);

    /**
     * 创建角色。
     *
     * @param tenantId 租户 ID
     * @param request  创建请求
     * @return 创建的角色
     */
    SysRole createRole(Long tenantId, CreateRoleRequest request);

    /**
     * 更新角色。
     *
     * @param id      角色 ID
     * @param request 更新请求
     * @return 更新后的角色
     */
    SysRole updateRole(Long id, UpdateRoleRequest request);

    /**
     * 删除角色。
     *
     * @param id 角色 ID
     */
    void deleteRole(Long id);

    /**
     * 分配自定义数据范围的部门（全量替换）。
     *
     * @param roleId  角色 ID
     * @param deptIds 部门 ID 列表
     */
    void assignDepts(Long roleId, List<Long> deptIds);

    /**
     * 获取角色的自定义数据范围部门 ID 列表。
     *
     * @param roleId 角色 ID
     * @return 部门 ID 列表
     */
    List<Long> getRoleDeptIds(Long roleId);

    /**
     * 获取租户下所有启用角色（下拉选项用）。
     *
     * @param tenantId 租户 ID
     * @return 角色列表
     */
    List<SysRole> listAllRoles(Long tenantId);
}
