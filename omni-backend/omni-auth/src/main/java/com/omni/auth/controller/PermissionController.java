package com.omni.auth.controller;

import com.omni.auth.service.PermissionService;
import com.omni.auth.service.PermissionTreeNode;
import com.omni.common.core.result.R;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 权限管理控制器。
 *
 * <p>提供权限树查询和角色权限分配接口，路径映射在 {@code /api/auth/permission}。
 * 支持完整权限树查看、角色关联权限查询及权限分配操作。</p>
 *
 * <h3>接口列表：</h3>
 * <ul>
 *   <li>{@code GET  /api/auth/permission/tree} — 获取完整权限树（权限码：{@code system:permission:list}）</li>
 *   <li>{@code GET  /api/auth/permission/role/{roleId}} — 获取角色权限树（带选中状态）（权限码：{@code system:role:list}）</li>
 *   <li>{@code POST /api/auth/permission/role/{roleId}/assign} — 分配权限给角色（权限码：{@code system:role:update}）</li>
 * </ul>
 *
 * @author Omni-Stack Team
 * @see PermissionService
 */
@Slf4j
@RestController
@RequestMapping("/api/auth/permission")
@RequiredArgsConstructor
public class PermissionController {

    private final PermissionService permissionService;

    /**
     * 获取完整权限树。
     *
     * <!-- 权限码: system:permission:list -->
     *
     * @param tenantId 租户 ID（由 Gateway 从 JWT 中提取并注入）
     * @return 权限树形结构 {@code R<List<PermissionTreeNode>>}
     */
    @GetMapping("/tree")
    @PreAuthorize("hasAuthority('system:permission:list')")
    public R<List<PermissionTreeNode>> tree(
            @RequestHeader(value = "X-Tenant-Id", defaultValue = "1") Long tenantId) {
        return R.ok(permissionService.getPermissionTree(tenantId));
    }

    /**
     * 获取角色的权限树（带选中状态）。
     *
     * <!-- 权限码: system:role:list -->
     *
     * @param roleId   角色 ID（路径变量）
     * @param tenantId 租户 ID（由 Gateway 注入）
     * @return 带 checked 标记的权限树 {@code R<List<PermissionTreeNode>>}
     */
    @GetMapping("/role/{roleId}")
    @PreAuthorize("hasAuthority('system:role:list')")
    public R<List<PermissionTreeNode>> rolePermissionTree(
            @PathVariable Long roleId,
            @RequestHeader(value = "X-Tenant-Id", defaultValue = "1") Long tenantId) {
        return R.ok(permissionService.getRolePermissionTree(roleId, tenantId));
    }

    /**
     * 分配权限给角色（全量替换）。
     *
     * <!-- 权限码: system:role:update -->
     *
     * @param roleId        角色 ID（路径变量）
     * @param permissionIds 权限 ID 列表（请求体）
     * @return 操作结果
     */
    @PostMapping("/role/{roleId}/assign")
    @PreAuthorize("hasAuthority('system:role:update')")
    public R<Void> assignPermissions(@PathVariable Long roleId,
                                     @RequestBody List<Long> permissionIds) {
        permissionService.assignPermissions(roleId, permissionIds);
        return R.ok();
    }
}
