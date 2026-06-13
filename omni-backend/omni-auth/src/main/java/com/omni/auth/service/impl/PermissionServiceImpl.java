package com.omni.auth.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.omni.auth.entity.SysPermission;
import com.omni.auth.mapper.SysPermissionMapper;
import com.omni.auth.mapper.SysRolePermissionMapper;
import com.omni.auth.service.PermissionService;
import com.omni.auth.service.PermissionTreeNode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 权限服务实现类。
 * <p>基于物化路径构建权限树，支持角色权限的查询和分配操作。</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PermissionServiceImpl implements PermissionService {

    private final SysPermissionMapper sysPermissionMapper;
    private final SysRolePermissionMapper sysRolePermissionMapper;

    /**
     * {@inheritDoc}
     *
     * <p>查询租户全部权限记录，按 sort 排序后构建树形结构。</p>
     */
    @Override
    public List<PermissionTreeNode> getPermissionTree(Long tenantId) {
        List<SysPermission> all = sysPermissionMapper.selectList(
                new LambdaQueryWrapper<SysPermission>()
                        .eq(SysPermission::getTenantId, tenantId)
                        .orderByAsc(SysPermission::getSort));
        return buildTree(all, 0L, null);
    }

    /**
     * {@inheritDoc}
     *
     * <p>查询角色已分配的权限 ID 集合，构建树时标记 checked 状态。</p>
     */
    @Override
    public List<PermissionTreeNode> getRolePermissionTree(Long roleId, Long tenantId) {
        List<SysPermission> all = sysPermissionMapper.selectList(
                new LambdaQueryWrapper<SysPermission>()
                        .eq(SysPermission::getTenantId, tenantId)
                        .orderByAsc(SysPermission::getSort));
        Set<Long> assignedIds = Set.copyOf(sysRolePermissionMapper.selectPermissionIdsByRoleId(roleId));
        return buildTree(all, 0L, assignedIds);
    }

    /**
     * {@inheritDoc}
     *
     * <p>先删除角色的全部权限关联，再批量插入新关联（全量替换策略）。</p>
     */
    @Override
    @Transactional
    public void assignPermissions(Long roleId, List<Long> permissionIds) {
        sysRolePermissionMapper.deleteByRoleId(roleId);
        if (permissionIds != null && !permissionIds.isEmpty()) {
            sysRolePermissionMapper.batchInsert(roleId, permissionIds);
        }
        log.info("已为角色 {} 分配 {} 个权限", roleId, permissionIds == null ? 0 : permissionIds.size());
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public List<Long> getRolePermissionIds(Long roleId) {
        return sysRolePermissionMapper.selectPermissionIdsByRoleId(roleId);
    }

    /**
     * 递归构建权限树。
     *
     * @param all         全部权限记录
     * @param parentId    当前父级 ID
     * @param assignedIds 已分配的权限 ID 集合（null 表示不标记选中状态）
     * @return 树形节点列表
     */
    private List<PermissionTreeNode> buildTree(List<SysPermission> all, Long parentId, Set<Long> assignedIds) {
        Map<Long, List<SysPermission>> grouped = all.stream()
                .collect(Collectors.groupingBy(SysPermission::getParentId));

        return buildChildren(grouped, parentId, assignedIds);
    }

    /**
     * 递归构建子节点。
     */
    private List<PermissionTreeNode> buildChildren(Map<Long, List<SysPermission>> grouped, Long parentId, Set<Long> assignedIds) {
        List<SysPermission> children = grouped.getOrDefault(parentId, new ArrayList<>());
        return children.stream()
                .map(p -> PermissionTreeNode.builder()
                        .id(p.getId())
                        .parentId(p.getParentId())
                        .permissionCode(p.getPermissionCode())
                        .permissionName(p.getPermissionName())
                        .type(p.getType())
                        .path(p.getPath())
                        .depth(p.getDepth())
                        .sort(p.getSort())
                        .status(p.getStatus())
                        .checked(assignedIds != null && assignedIds.contains(p.getId()))
                        .children(buildChildren(grouped, p.getId(), assignedIds))
                        .build())
                .collect(Collectors.toList());
    }
}
