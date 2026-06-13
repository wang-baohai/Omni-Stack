package com.omni.auth.controller;

import com.omni.auth.dto.CreateOrgUnitRequest;
import com.omni.auth.dto.UpdateOrgUnitRequest;
import com.omni.auth.entity.SysOrgUnit;
import com.omni.auth.service.OrgUnitService;
import com.omni.auth.service.OrgUnitTreeNode;
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
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 组织管理控制器。
 * <p>提供组织单元树 CRUD 接口，路径映射在 {@code /api/auth/org}。</p>
 *
 * @see OrgUnitService
 */
@Slf4j
@RestController
@RequestMapping("/api/auth/org")
@RequiredArgsConstructor
public class OrgUnitController {

    private final OrgUnitService orgUnitService;

    /**
     * 获取组织树。
     *
     * @param tenantId 租户 ID
     * @return 组织树形结构
     */
    @GetMapping("/tree")
    @PreAuthorize("hasAuthority('system:org:list')")
    public R<List<OrgUnitTreeNode>> tree(
            @RequestHeader(value = "X-Tenant-Id", defaultValue = "1") Long tenantId) {
        return R.ok(orgUnitService.getOrgTree(tenantId));
    }

    /**
     * 获取组织单元详情。
     *
     * @param id 组织单元 ID
     * @return 组织单元实体
     */
    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('system:org:list')")
    public R<SysOrgUnit> getById(@PathVariable Long id) {
        return R.ok(orgUnitService.getById(id));
    }

    /**
     * 创建组织单元。
     *
     * @param request  创建请求
     * @param tenantId 租户 ID
     * @return 创建的组织单元
     */
    @PostMapping
    @PreAuthorize("hasAuthority('system:org:create')")
    public R<SysOrgUnit> create(@Valid @RequestBody CreateOrgUnitRequest request,
                                @RequestHeader(value = "X-Tenant-Id", defaultValue = "1") Long tenantId) {
        return R.ok(orgUnitService.createOrgUnit(tenantId, request));
    }

    /**
     * 更新组织单元。
     *
     * @param id      组织单元 ID
     * @param request 更新请求
     * @return 更新后的组织单元
     */
    @PutMapping("/{id}")
    @PreAuthorize("hasAuthority('system:org:update')")
    public R<SysOrgUnit> update(@PathVariable Long id,
                                @Valid @RequestBody UpdateOrgUnitRequest request) {
        return R.ok(orgUnitService.updateOrgUnit(id, request));
    }

    /**
     * 删除组织单元（级联删除后代节点）。
     *
     * @param id 组织单元 ID
     * @return 操作结果
     */
    @DeleteMapping("/{id}")
    @PreAuthorize("hasAuthority('system:org:delete')")
    public R<Void> delete(@PathVariable Long id) {
        orgUnitService.deleteOrgUnit(id);
        return R.ok();
    }
}
