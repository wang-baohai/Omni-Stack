package com.omni.auth.controller;

import com.omni.auth.dto.CreateUserRequest;
import com.omni.auth.dto.UpdateUserRequest;
import com.omni.auth.dto.UserVO;
import com.omni.auth.service.UserService;
import com.omni.common.core.result.PageResult;
import com.omni.common.core.result.R;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
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
 *
 * <p>提供用户 CRUD、角色分配和状态切换接口，路径映射在 {@code /api/auth/user}。
 * 所有写操作均会记录操作人和客户端 IP 用于审计追踪。</p>
 *
 * <h3>接口列表：</h3>
 * <ul>
 *   <li>{@code GET    /api/auth/user/list} — 分页查询用户（权限码：{@code system:user:list}）</li>
 *   <li>{@code GET    /api/auth/user/{id}} — 根据 ID 查询用户（权限码：{@code system:user:list}）</li>
 *   <li>{@code POST   /api/auth/user} — 创建用户（权限码：{@code system:user:create}）</li>
 *   <li>{@code PUT    /api/auth/user/{id}} — 更新用户信息（权限码：{@code system:user:update}）</li>
 *   <li>{@code DELETE /api/auth/user/{id}} — 删除用户（权限码：{@code system:user:delete}）</li>
 *   <li>{@code POST   /api/auth/user/{userId}/roles} — 分配用户角色（权限码：{@code system:user:update}）</li>
 *   <li>{@code GET    /api/auth/user/{userId}/roles} — 获取用户角色 ID 列表（权限码：{@code system:user:list}）</li>
 *   <li>{@code PUT    /api/auth/user/{userId}/status} — 切换用户启用/禁用（权限码：{@code system:user:update}）</li>
 * </ul>
 *
 * @author Omni-Stack Team
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
     * <!-- 权限码: system:user:list -->
     *
     * @param id 用户 ID（路径变量）
     * @return 用户实体 {@code R<SysUser>}
     */
    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('system:user:list')")
    public R<UserVO> getById(@PathVariable Long id,
                             @RequestHeader("X-Tenant-Id") Long tenantId) {
        return R.ok(userService.getUserDetail(id, tenantId));
    }

    /**
     * 分页查询用户列表。
     *
     * <!-- 权限码: system:user:list -->
     *
     * @param page     页码（默认 1）
     * @param size     每页大小（默认 10）
     * @param tenantId 租户 ID（由 Gateway 注入）
     * @return 分页用户列表 {@code R<PageResult<SysUser>>}
     */
    @GetMapping("/list")
    @PreAuthorize("hasAuthority('system:user:list')")
    public R<PageResult<UserVO>> list(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestHeader("X-Tenant-Id") Long tenantId) {
        return R.ok(userService.listUsers(tenantId, page, size));
    }

    /**
     * 创建新用户。
     *
     * <!-- 权限码: system:user:create -->
     *
     * @param request     创建用户请求（含用户名、密码、租户 ID 等）
     * @param httpRequest HTTP 请求，用于提取操作人 IP
     * @return 操作结果
     */
    @PostMapping
    @PreAuthorize("hasAuthority('system:user:create')")
    public R<Void> create(@Valid @RequestBody CreateUserRequest request,
                          HttpServletRequest httpRequest) {
        String operator = getCurrentUsername();
        String ip = extractClientIp(httpRequest);
        userService.createUser(request, operator, ip);
        return R.ok();
    }

    /**
     * 更新用户信息。
     *
     * <!-- 权限码: system:user:update -->
     *
     * @param id   用户 ID（路径变量）
     * @param user 更新后的用户实体（请求体）
     * @return 操作结果
     */
    @PutMapping("/{id}")
    @PreAuthorize("hasAuthority('system:user:update')")
    public R<Void> update(@PathVariable Long id,
                          @RequestHeader("X-Tenant-Id") Long tenantId,
                          @Valid @RequestBody UpdateUserRequest request) {
        userService.updateUser(id, tenantId, request);
        return R.ok();
    }

    /**
     * 删除用户。
     *
     * <!-- 权限码: system:user:delete -->
     *
     * @param id          用户 ID（路径变量）
     * @param httpRequest HTTP 请求，用于提取操作人 IP
     * @return 操作结果
     */
    @DeleteMapping("/{id}")
    @PreAuthorize("hasAuthority('system:user:delete')")
    public R<Void> delete(@PathVariable Long id,
                          @RequestHeader("X-Tenant-Id") Long tenantId,
                          HttpServletRequest httpRequest) {
        String operator = getCurrentUsername();
        String ip = extractClientIp(httpRequest);
        userService.deleteUser(id, tenantId, operator, ip);
        return R.ok();
    }

    /**
     * 分配用户角色（全量替换）。
     *
     * <!-- 权限码: system:user:update -->
     *
     * @param userId      用户 ID（路径变量）
     * @param roleIds     角色 ID 列表（请求体）
     * @param httpRequest HTTP 请求，用于提取操作人 IP
     * @return 操作结果
     */
    @PostMapping("/{userId}/roles")
    @PreAuthorize("hasAuthority('system:user:update')")
    public R<Void> assignRoles(@PathVariable Long userId,
                               @RequestHeader("X-Tenant-Id") Long tenantId,
                               @RequestBody List<Long> roleIds,
                               HttpServletRequest httpRequest) {
        String operator = getCurrentUsername();
        String ip = extractClientIp(httpRequest);
        userService.assignRoles(userId, tenantId, roleIds, operator, ip);
        return R.ok();
    }

    /**
     * 获取用户已分配的角色 ID 列表。
     *
     * <!-- 权限码: system:user:list -->
     *
     * @param userId 用户 ID（路径变量）
     * @return 角色 ID 列表 {@code R<List<Long>>}
     */
    @GetMapping("/{userId}/roles")
    @PreAuthorize("hasAuthority('system:user:list')")
    public R<List<Long>> getUserRoleIds(@PathVariable Long userId,
                                        @RequestHeader("X-Tenant-Id") Long tenantId) {
        return R.ok(userService.getUserRoleIds(userId, tenantId));
    }

    /**
     * 切换用户启用/禁用状态。
     *
     * <!-- 权限码: system:user:update -->
     *
     * @param userId      用户 ID（路径变量）
     * @param status      目标状态：1-启用, 0-禁用
     * @param httpRequest HTTP 请求，用于提取操作人 IP
     * @return 操作结果
     */
    @PutMapping("/{userId}/status")
    @PreAuthorize("hasAuthority('system:user:update')")
    public R<Void> toggleStatus(@PathVariable Long userId,
                                @RequestHeader("X-Tenant-Id") Long tenantId,
                                @RequestParam Integer status,
                                HttpServletRequest httpRequest) {
        String operator = getCurrentUsername();
        String ip = extractClientIp(httpRequest);
        userService.toggleStatus(userId, tenantId, status, operator, ip);
        return R.ok();
    }

    /**
     * 获取当前认证用户的用户名。
     *
     * @return 用户名，未认证时返回 "anonymous"
     */
    private String getCurrentUsername() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        return auth != null ? auth.getName() : "anonymous";
    }

    /**
     * 提取客户端 IP 地址。
     * <p>优先读取 X-Forwarded-For 头（取第一个值），回退到 remoteAddr。</p>
     *
     * @param request HTTP 请求
     * @return 客户端 IP 地址
     */
    private String extractClientIp(HttpServletRequest request) {
        String xForwardedFor = request.getHeader("X-Forwarded-For");
        if (xForwardedFor != null && !xForwardedFor.isBlank()) {
            return xForwardedFor.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }
}
