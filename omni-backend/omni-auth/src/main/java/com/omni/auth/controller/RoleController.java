package com.omni.auth.controller;

import com.omni.auth.dto.CreateRoleRequest;
import com.omni.auth.dto.UpdateRoleRequest;
import com.omni.auth.entity.SysRole;
import com.omni.auth.service.RoleService;
import com.omni.common.core.result.PageResult;
import com.omni.common.core.result.R;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 角色管理控制器。
 * <p>提供角色 CRUD、部门分配接口，路径映射在 {@code /api/auth/role}。</p>
 *
 * @see RoleService
 */
@Slf4j
@RestController
@RequestMapping("/api/auth/role")
@RequiredArgsConstructor
public class RoleController {

    private final RoleService roleService;

    /**
     * 分页查询角色列表。
     *
     * @param page     页码（默认 1）
     * @param size     每页大小（默认 10）
     * @param tenantId 租户 ID
     * @return 角色分页结果
     */
    @GetMapping("/list")
    @PreAuthorize("hasAuthority('system:role:list')")
    public R<PageResult<SysRole>> list(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestHeader(value = "X-Tenant-Id", defaultValue = "1") Long tenantId) {
        return R.ok(roleService.listRoles(tenantId, page, size));
    }

    /**
     * 获取角色详情。
     *
     * @param id 角色 ID
     * @return 角色实体
     */
    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('system:role:list')")
    public R<SysRole> getById(@PathVariable Long id) {
        return R.ok(roleService.getById(id));
    }

    /**
     * 创建角色。
     *
     * @param request  创建请求
     * @param tenantId 租户 ID
     * @return 创建的角色
     */
    @PostMapping
    @PreAuthorize("hasAuthority('system:role:create')")
    public R<SysRole> create(@Valid @RequestBody CreateRoleRequest request,
                             @RequestHeader(value = "X-Tenant-Id", defaultValue = "1") Long tenantId) {
        return R.ok(roleService.createRole(tenantId, request));
    }

    /**
     * 更新角色。
     *
     * @param id      角色 ID
     * @param request 更新请求
     * @return 更新后的角色
     */
    @PutMapping("/{id}")
    @PreAuthorize("hasAuthority('system:role:update')")
    public R<SysRole> update(@PathVariable Long id,
                             @Valid @RequestBody UpdateRoleRequest request) {
        return R.ok(roleService.updateRole(id, request));
    }

    /**
     * 删除角色。
     *
     * @param id 角色 ID
     * @return 操作结果
     */
    @DeleteMapping("/{id}")
    @PreAuthorize("hasAuthority('system:role:delete')")
    public R<Void> delete(@PathVariable Long id) {
        roleService.deleteRole(id);
        return R.ok();
    }

    /**
     * 分配角色的自定义数据范围部门（全量替换）。
     *
     * @param roleId  角色 ID
     * @param deptIds 部门 ID 列表
     * @return 操作结果
     */
    @PostMapping("/{roleId}/depts")
    @PreAuthorize("hasAuthority('system:role:update')")
    public R<Void> assignDepts(@PathVariable Long roleId,
                               @RequestBody List<Long> deptIds) {
        roleService.assignDepts(roleId, deptIds);
        return R.ok();
    }

    /**
     * 获取角色关联的部门 ID 列表。
     *
     * @param roleId 角色 ID
     * @return 部门 ID 列表
     */
    @GetMapping("/{roleId}/depts")
    @PreAuthorize("hasAuthority('system:role:list')")
    public R<List<Long>> getRoleDeptIds(@PathVariable Long roleId) {
        return R.ok(roleService.getRoleDeptIds(roleId));
    }

    /**
     * 获取租户下所有启用角色（下拉选项用）。
     *
     * @param tenantId 租户 ID
     * @return 角色列表
     */
    @GetMapping("/all")
    public R<List<SysRole>> all(
            @RequestHeader(value = "X-Tenant-Id", defaultValue = "1") Long tenantId) {
        return R.ok(roleService.listAllRoles(tenantId));
    }
}
