package com.omni.auth.controller;

import com.omni.auth.entity.SysUser;
import com.omni.auth.service.UserService;
import com.omni.common.core.result.R;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 用户管理控制器。
 * <p>
 * 提供用户资源的增删改查接口，路径前缀为 {@code /api/auth/user}。
 * </p>
 */
@RestController
@RequestMapping("/api/auth/user")
@RequiredArgsConstructor
public class UserController {

    /** 用户服务实例 */
    private final UserService userService;

    /**
     * 根据 ID 查询用户。
     *
     * @param id 用户 ID
     * @return 用户实体
     */
    @GetMapping("/{id}")
    public R<SysUser> getById(@PathVariable Long id) {
        return R.ok(userService.getById(id));
    }

    /**
     * 分页查询用户列表。
     *
     * @param page     页码，默认 1
     * @param size     每页大小，默认 10
     * @param tenantId 租户 ID，默认 1
     * @return 分页用户列表
     */
    @GetMapping("/list")
    public R<com.omni.common.core.result.PageResult<SysUser>> list(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(defaultValue = "1") Long tenantId) {
        return R.ok(userService.listUsers(tenantId, page, size));
    }

    /**
     * 创建新用户。
     *
     * @param user 用户实体（请求体）
     * @return 成功响应
     */
    @PostMapping
    public R<Void> create(@RequestBody SysUser user) {
        userService.save(user);
        return R.ok();
    }

    /**
     * 更新用户信息。
     *
     * @param id   用户 ID
     * @param user 更新后的用户实体
     * @return 成功响应
     */
    @PutMapping("/{id}")
    public R<Void> update(@PathVariable Long id, @RequestBody SysUser user) {
        user.setId(id);
        userService.updateById(user);
        return R.ok();
    }

    /**
     * 删除用户。
     *
     * @param id 用户 ID
     * @return 成功响应
     */
    @DeleteMapping("/{id}")
    public R<Void> delete(@PathVariable Long id) {
        userService.removeById(id);
        return R.ok();
    }
}
