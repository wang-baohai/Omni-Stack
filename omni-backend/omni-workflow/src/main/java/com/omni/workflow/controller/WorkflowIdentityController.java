package com.omni.workflow.controller;

import com.omni.common.core.result.R;
import com.omni.workflow.dto.*;
import com.omni.workflow.service.WorkflowIdentityService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 工作流身份查询控制器。
 * <p>
 * 为流程设计器提供用户、角色、组织架构等身份数据。
 * 路径前缀：{@code /api/workflow/identity}。</p>
 *
 * @author Omni-Stack Team
 */
@RestController
@RequestMapping("/api/workflow/identity")
@RequiredArgsConstructor
public class WorkflowIdentityController {

    private final WorkflowIdentityService workflowIdentityService;

    /**
     * 查询用户列表。
     *
     * @param tenantId 租户 ID
     * @param keyword  关键字（用户名/昵称，可选）
     * @return 用户列表
     */
    @GetMapping("/users")
    @PreAuthorize("hasAuthority('workflow:identity:list')")
    public R<List<IdentityUserVO>> listUsers(
            @RequestHeader("X-Tenant-Id") Long tenantId,
            @RequestParam(required = false) String keyword) {
        return R.ok(workflowIdentityService.listUsers(tenantId, keyword));
    }

    /**
     * 查询角色列表。
     *
     * @param tenantId 租户 ID
     * @return 角色列表
     */
    @GetMapping("/roles")
    @PreAuthorize("hasAuthority('workflow:identity:list')")
    public R<List<IdentityRoleVO>> listRoles(
            @RequestHeader("X-Tenant-Id") Long tenantId) {
        return R.ok(workflowIdentityService.listRoles(tenantId));
    }

    /**
     * 获取组织架构树。
     *
     * @param tenantId 租户 ID
     * @return 组织树节点列表
     */
    @GetMapping("/org-tree")
    @PreAuthorize("hasAuthority('workflow:identity:list')")
    public R<List<OrgTreeNodeVO>> getOrgTree(
            @RequestHeader("X-Tenant-Id") Long tenantId) {
        return R.ok(workflowIdentityService.getOrgTree(tenantId));
    }

    /**
     * 获取组织单元下拉选项（树形结构）。
     *
     * @param tenantId 租户 ID
     * @return 组织单元列表
     */
    @GetMapping("/unit-options")
    @PreAuthorize("hasAuthority('workflow:identity:list')")
    public R<List<OrgTreeNodeVO>> getUnitOptions(
            @RequestHeader("X-Tenant-Id") Long tenantId) {
        return R.ok(workflowIdentityService.getUnitOptions(tenantId));
    }

    /**
     * 模拟解析审批候选人（设计时预览）。
     *
     * @param tenantId 租户 ID
     * @param request  解析请求
     * @return 解析结果
     */
    @PostMapping("/resolve-preview")
    @PreAuthorize("hasAuthority('workflow:identity:list')")
    public R<ResolvePreviewResult> resolvePreview(
            @RequestHeader("X-Tenant-Id") Long tenantId,
            @Valid @RequestBody ResolvePreviewRequest request) {
        return R.ok(workflowIdentityService.resolvePreview(request, tenantId));
    }
}
