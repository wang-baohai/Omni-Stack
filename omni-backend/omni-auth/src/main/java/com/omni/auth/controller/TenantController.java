package com.omni.auth.controller;

import java.util.List;

import com.omni.auth.dto.CreateTenantRequest;
import com.omni.auth.dto.UpdateTenantRequest;
import com.omni.auth.entity.SysTenant;
import com.omni.auth.entity.SysTenantModuleProvision;
import com.omni.auth.service.TenantProvisionService;
import com.omni.auth.service.TenantService;
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
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 租户管理控制器。
 *
 * <p>提供租户 CRUD 接口，路径映射在 {@code /api/auth/tenant}。
 * 支持租户的增删改查操作，用于多租户体系下的租户生命周期管理。</p>
 *
 * <h3>接口列表：</h3>
 * <ul>
 *   <li>{@code GET    /api/auth/tenant/list} — 分页查询租户（权限码：{@code system:tenant:list}）</li>
 *   <li>{@code GET    /api/auth/tenant/{id}} — 获取租户详情（权限码：{@code system:tenant:list}）</li>
 *   <li>{@code POST   /api/auth/tenant} — 创建租户（权限码：{@code system:tenant:create}）</li>
 *   <li>{@code PUT    /api/auth/tenant/{id}} — 更新租户（权限码：{@code system:tenant:update}）</li>
 *   <li>{@code GET    /api/auth/tenant/{id}/provisioning} — 查询初始化明细</li>
 *   <li>{@code POST   /api/auth/tenant/{id}/provisioning/retry} — 重试失败模块</li>
 *   <li>{@code DELETE /api/auth/tenant/{id}} — 删除租户（权限码：{@code system:tenant:delete}）</li>
 * </ul>
 *
 * @author Omni-Stack Team
 * @see TenantService
 */
@Slf4j
@RestController
@RequestMapping("/api/auth/tenant")
@RequiredArgsConstructor
public class TenantController {

    private final TenantService tenantService;
    private final TenantProvisionService tenantProvisionService;

    /**
     * 分页查询租户列表。
     *
     * <!-- 权限码: system:tenant:list -->
     *
     * @param page 页码（默认 1）
     * @param size 每页大小（默认 10）
     * @return 租户分页结果 {@code R<PageResult<SysTenant>>}
     */
    @GetMapping("/list")
    @PreAuthorize("hasAuthority('system:tenant:list')")
    public R<PageResult<SysTenant>> list(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int size) {
        return R.ok(tenantService.listTenants(page, size));
    }

    /**
     * 获取租户详情。
     *
     * <!-- 权限码: system:tenant:list -->
     *
     * @param id 租户 ID（路径变量）
     * @return 租户实体 {@code R<SysTenant>}
     */
    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('system:tenant:list')")
    public R<SysTenant> getById(@PathVariable Long id) {
        return R.ok(tenantService.getById(id));
    }

    /**
     * 创建租户。
     *
     * <!-- 权限码: system:tenant:create -->
     *
     * @param request 创建请求（含租户名称、编码、联系人等）
     * @return 创建的租户 {@code R<SysTenant>}
     */
    @PostMapping
    @PreAuthorize("hasAuthority('system:tenant:create')")
    public R<SysTenant> create(@Valid @RequestBody CreateTenantRequest request) {
        return R.ok(tenantService.createTenant(request));
    }

    /**
     * 更新租户。
     *
     * <!-- 权限码: system:tenant:update -->
     *
     * @param id      租户 ID（路径变量）
     * @param request 更新请求
     * @return 更新后的租户 {@code R<SysTenant>}
     */
    @PutMapping("/{id}")
    @PreAuthorize("hasAuthority('system:tenant:update')")
    public R<SysTenant> update(@PathVariable Long id,
                               @Valid @RequestBody UpdateTenantRequest request) {
        return R.ok(tenantService.updateTenant(id, request));
    }

    /**
     * 查询租户各模块初始化状态。
     *
     * @param id 租户 ID
     * @return 模块初始化明细
     */
    @GetMapping("/{id}/provisioning")
    @PreAuthorize("hasAuthority('system:tenant:list')")
    public R<List<SysTenantModuleProvision>> provisioning(@PathVariable Long id) {
        return R.ok(tenantProvisionService.listModuleStates(id));
    }

    /**
     * 重试租户初始化失败模块。
     *
     * @param id 租户 ID
     * @return 操作结果
     */
    @PostMapping("/{id}/provisioning/retry")
    @PreAuthorize("hasAuthority('system:tenant:update')")
    public R<Void> retryProvisioning(@PathVariable Long id) {
        tenantProvisionService.retryFailedModules(id);
        return R.ok();
    }

    /**
     * 删除租户。
     *
     * <!-- 权限码: system:tenant:delete -->
     *
     * @param id 租户 ID（路径变量）
     * @return 操作结果
     */
    @DeleteMapping("/{id}")
    @PreAuthorize("hasAuthority('system:tenant:delete')")
    public R<Void> delete(@PathVariable Long id) {
        tenantService.deleteTenant(id);
        return R.ok();
    }
}
