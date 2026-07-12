package com.omni.auth.controller;

import com.omni.auth.service.PermissionService;
import com.omni.auth.service.PermissionTreeNode;
import com.omni.common.core.result.R;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 动态菜单控制器。
 *
 * <p>为前端提供动态菜单数据，基于当前用户的权限集合过滤出有权访问的
 * DIRECTORY 和 MENU 类型节点。路径映射在 {@code /api/auth/menus}。</p>
 *
 * <h3>接口列表：</h3>
 * <ul>
 *   <li>{@code GET /api/auth/menus} — 获取当前用户的动态菜单树（基于 JWT 中的权限自动过滤）</li>
 * </ul>
 *
 * @author Omni-Stack Team
 * @see PermissionService
 */
@Slf4j
@RestController
@RequestMapping("/api/auth/menus")
@RequiredArgsConstructor
public class MenuController {

    private final PermissionService permissionService;

    /**
     * 获取当前用户的动态菜单树。
     *
     * <p>查询当前用户所在租户的权限树，仅返回当前用户有权访问的
     * DIRECTORY 和 MENU 类型节点。前端据此动态注册路由和渲染侧边栏菜单。</p>
     *
     * @param tenantId 租户 ID（由 Gateway 注入）
     * @return 过滤后的菜单树形结构
     */
    @GetMapping
    public R<List<PermissionTreeNode>> getMenus(
            @RequestHeader("X-Tenant-Id") Long tenantId) {
        List<PermissionTreeNode> tree = permissionService.getPermissionTree(tenantId);
        // 第一步：过滤为 DIRECTORY 和 MENU 类型节点
        List<PermissionTreeNode> menus = filterMenuNodes(tree);
        // 第二步：根据当前用户权限集合过滤
        Set<String> userPermissionCodes = getCurrentUserPermissions();
        menus = filterByUserPermissions(menus, userPermissionCodes);
        return R.ok(menus);
    }

    /**
     * 从 SecurityContext 中提取当前用户的权限编码集合。
     * <p>排除 {@code ROLE_} 前缀的角色 authority，仅保留 API 级权限编码。</p>
     *
     * @return 用户权限编码集合
     */
    private Set<String> getCurrentUserPermissions() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || auth.getAuthorities() == null) {
            return Set.of();
        }
        return auth.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .filter(a -> !a.startsWith("ROLE_"))
                .collect(Collectors.toSet());
    }

    /**
     * 递归过滤权限树，仅保留 DIRECTORY 和 MENU 类型节点。
     *
     * @param nodes 权限树节点列表
     * @return 过滤后的菜单节点列表
     */
    private List<PermissionTreeNode> filterMenuNodes(List<PermissionTreeNode> nodes) {
        return nodes.stream()
                .filter(n -> "DIRECTORY".equals(n.getType()) || "MENU".equals(n.getType()))
                .map(n -> PermissionTreeNode.builder()
                        .id(n.getId())
                        .parentId(n.getParentId())
                        .permissionCode(n.getPermissionCode())
                        .permissionName(n.getPermissionName())
                        .type(n.getType())
                        .path(n.getPath())
                        .depth(n.getDepth())
                        .sort(n.getSort())
                        .status(n.getStatus())
                        .checked(n.getChecked())
                        .children(n.getChildren() != null ? filterMenuNodes(n.getChildren()) : List.of())
                        .build())
                .collect(Collectors.toList());
    }

    /**
     * 根据用户权限集合递归过滤菜单树。
     * <p>MENU 节点：保留当用户拥有对应权限编码时。
     * DIRECTORY 节点：先递归过滤子节点，仅当有可见子节点时保留。</p>
     *
     * @param nodes       菜单树节点列表
     * @param permissions 用户权限编码集合
     * @return 过滤后的菜单节点列表
     */
    private List<PermissionTreeNode> filterByUserPermissions(List<PermissionTreeNode> nodes,
                                                              Set<String> permissions) {
        if (permissions.isEmpty()) {
            // 无法获取权限信息时返回全部菜单（降级处理）
            return nodes;
        }
        return nodes.stream()
                .map(node -> {
                    if ("DIRECTORY".equals(node.getType())) {
                        // 先递归过滤子节点
                        List<PermissionTreeNode> filteredChildren =
                                node.getChildren() != null
                                        ? filterByUserPermissions(node.getChildren(), permissions)
                                        : List.of();
                        // 仅当有可见子节点时保留目录节点
                        if (filteredChildren.isEmpty()) {
                            return null;
                        }
                        return PermissionTreeNode.builder()
                                .id(node.getId())
                                .parentId(node.getParentId())
                                .permissionCode(node.getPermissionCode())
                                .permissionName(node.getPermissionName())
                                .type(node.getType())
                                .path(node.getPath())
                                .depth(node.getDepth())
                                .sort(node.getSort())
                                .status(node.getStatus())
                                .checked(node.getChecked())
                                .children(filteredChildren)
                                .build();
                    } else if ("MENU".equals(node.getType())) {
                        // 保留用户有权限的菜单节点
                        if (permissions.contains(node.getPermissionCode())) {
                            return PermissionTreeNode.builder()
                                    .id(node.getId())
                                    .parentId(node.getParentId())
                                    .permissionCode(node.getPermissionCode())
                                    .permissionName(node.getPermissionName())
                                    .type(node.getType())
                                    .path(node.getPath())
                                    .depth(node.getDepth())
                                    .sort(node.getSort())
                                    .status(node.getStatus())
                                    .checked(node.getChecked())
                                    .children(node.getChildren())
                                    .build();
                        }
                        return null;
                    }
                    return null;
                })
                .filter(node -> node != null)
                .collect(Collectors.toList());
    }
}
