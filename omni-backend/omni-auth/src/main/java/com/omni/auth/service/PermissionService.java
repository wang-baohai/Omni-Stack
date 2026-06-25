package com.omni.auth.service;

import com.omni.auth.entity.SysPermission;

import java.util.List;

/**
 * 权限服务接口，提供权限树构建和角色权限分配操作。
 * <p>支持完整权限树查询和角色关联权限树查询（带 checked 标记）。</p>
 *
 * @author Omni-Stack Team
 * @see PermissionTreeNode
 * @see com.omni.auth.entity.SysPermission
 */
public interface PermissionService {

    /**
     * 获取指定租户的完整权限树。
     *
     * @param tenantId 租户 ID
     * @return 权限树形列表（仅包含顶级节点，子节点嵌套在 children 中）
     */
    List<PermissionTreeNode> getPermissionTree(Long tenantId);

    /**
     * 获取指定角色的权限树（已分配节点标记 checked=true）。
     *
     * @param roleId   角色 ID
     * @param tenantId 租户 ID
     * @return 带选中状态的权限树
     */
    List<PermissionTreeNode> getRolePermissionTree(Long roleId, Long tenantId);

    /**
     * 分配权限给角色（全量替换）。
     *
     * @param roleId        角色 ID
     * @param permissionIds 权限 ID 列表
     */
    void assignPermissions(Long roleId, List<Long> permissionIds);

    /**
     * 获取角色的已分配权限 ID 列表。
     *
     * @param roleId 角色 ID
     * @return 权限 ID 列表
     */
    List<Long> getRolePermissionIds(Long roleId);
}
