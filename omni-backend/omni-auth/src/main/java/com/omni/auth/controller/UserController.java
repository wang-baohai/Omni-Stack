package com.omni.auth.controller;

import com.omni.auth.entity.SysUser;
import com.omni.auth.service.UserService;
import com.omni.common.core.result.PageResult;
import com.omni.common.core.result.R;
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
 * 用户管理控制器。
 * <p>提供用户 CRUD、角色分配和状态切换接口，路径映射在 {@code /api/auth/user}。</p>
 *
 * @see UserService
 */
@Slf4j
@RestController
@RequestMapping("/api/auth/user")
@RequiredArgsConstructor
public class UserController {

    private final UserService userService;

    /**
     * 根据 ID 查询用户。
     *
     * @param id 用户 ID
     * @return 用户实体
     */
    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('system:user:list')")
    public R<SysUser> getById(@PathVariable Long id) {
        return R.ok(userService.getById(id));
    }

    /**
     * 分页查询用户列表。
     *
     * @param page     页码（默认 1）
     * @param size     每页大小（默认 10）
     * @param tenantId 租户 ID（由 Gateway 注入）
     * @return 分页用户列表
     */
    @GetMapping("/list")
    @PreAuthorize("hasAuthority('system:user:list')")
    public R<PageResult<SysUser>> list(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestHeader(value = "X-Tenant-Id", defaultValue = "1") Long tenantId) {
        return R.ok(userService.listUsers(tenantId, page, size));
    }

    /**
     * 创建新用户。
     *
     * @param user 用户实体（请求体）
     * @return 操作结果
     */
    @PostMapping
    @PreAuthorize("hasAuthority('system:user:create')")
    public R<Void> create(@RequestBody SysUser user) {
        userService.save(user);
        return R.ok();
    }

    /**
     * 更新用户信息。
     *
     * @param id   用户 ID
     * @param user 更新后的用户实体
     * @return 操作结果
     */
    @PutMapping("/{id}")
    @PreAuthorize("hasAuthority('system:user:update')")
    public R<Void> update(@PathVariable Long id, @RequestBody SysUser user) {
        user.setId(id);
        userService.updateById(user);
        return R.ok();
    }

    /**
     * 删除用户。
     *
     * @param id 用户 ID
     * @return 操作结果
     */
    @DeleteMapping("/{id}")
    @PreAuthorize("hasAuthority('system:user:delete')")
    public R<Void> delete(@PathVariable Long id) {
        userService.removeById(id);
        return R.ok();
    }

    /**
     * 分配用户角色（全量替换）。
     *
     * @param userId  用户 ID
     * @param roleIds 角色 ID 列表
     * @return 操作结果
     */
    @PostMapping("/{userId}/roles")
    @PreAuthorize("hasAuthority('system:user:update')")
    public R<Void> assignRoles(@PathVariable Long userId,
                               @RequestBody List<Long> roleIds) {
        userService.assignRoles(userId, roleIds);
        return R.ok();
    }

    /**
     * 获取用户已分配的角色 ID 列表。
     *
     * @param userId 用户 ID
     * @return 角色 ID 列表
     */
    @GetMapping("/{userId}/roles")
    @PreAuthorize("hasAuthority('system:user:list')")
    public R<List<Long>> getUserRoleIds(@PathVariable Long userId) {
        return R.ok(userService.getUserRoleIds(userId));
    }

    /**
     * 切换用户启用/禁用状态。
     *
     * @param userId 用户 ID
     * @param status 目标状态：1-启用, 0-禁用
     * @return 操作结果
     */
    @PutMapping("/{userId}/status")
    @PreAuthorize("hasAuthority('system:user:update')")
    public R<Void> toggleStatus(@PathVariable Long userId,
                                @RequestParam Integer status) {
        userService.toggleStatus(userId, status);
        return R.ok();
    }
}
