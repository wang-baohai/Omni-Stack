package com.omni.auth.controller;

import com.omni.auth.dto.CreateTenantRequest;
import com.omni.auth.dto.UpdateTenantRequest;
import com.omni.auth.entity.SysTenant;
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
 * <p>提供租户 CRUD 接口，路径映射在 {@code /api/auth/tenant}。</p>
 *
 * @see TenantService
 */
@Slf4j
@RestController
@RequestMapping("/api/auth/tenant")
@RequiredArgsConstructor
public class TenantController {

    private final TenantService tenantService;

    /**
     * 分页查询租户列表。
     *
     * @param page 页码（默认 1）
     * @param size 每页大小（默认 10）
     * @return 租户分页结果
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
     * @param id 租户 ID
     * @return 租户实体
     */
    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('system:tenant:list')")
    public R<SysTenant> getById(@PathVariable Long id) {
        return R.ok(tenantService.getById(id));
    }

    /**
     * 创建租户。
     *
     * @param request 创建请求
     * @return 创建的租户
     */
    @PostMapping
    @PreAuthorize("hasAuthority('system:tenant:create')")
    public R<SysTenant> create(@Valid @RequestBody CreateTenantRequest request) {
        return R.ok(tenantService.createTenant(request));
    }

    /**
     * 更新租户。
     *
     * @param id      租户 ID
     * @param request 更新请求
     * @return 更新后的租户
     */
    @PutMapping("/{id}")
    @PreAuthorize("hasAuthority('system:tenant:update')")
    public R<SysTenant> update(@PathVariable Long id,
                               @Valid @RequestBody UpdateTenantRequest request) {
        return R.ok(tenantService.updateTenant(id, request));
    }

    /**
     * 删除租户。
     *
     * @param id 租户 ID
     * @return 操作结果
     */
    @DeleteMapping("/{id}")
    @PreAuthorize("hasAuthority('system:tenant:delete')")
    public R<Void> delete(@PathVariable Long id) {
        tenantService.deleteTenant(id);
        return R.ok();
    }
}
