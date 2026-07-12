package com.omni.workflow.controller;

import com.omni.common.core.result.R;
import com.omni.workflow.dto.ApprovalRequest;
import com.omni.workflow.service.WorkflowApprovalService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

/**
 * 审批操作控制器。
 * <p>
 * 提供审批通过/驳回、加签、减签、委托等操作接口。
 * 封装 {@link ApprovalService} 的 Multi-Instance 会签能力。
 * </p>
 *
 * @author Omni-Stack Team
 */
@RestController
@RequestMapping("/api/workflow/approval")
@RequiredArgsConstructor
@Validated
public class ApprovalController {

    private final WorkflowApprovalService workflowApprovalService;

    /**
     * 审批通过或驳回。
     *
     * @param taskId  任务 ID（路径参数）
     * @param request 审批请求（通过/驳回 + 意见 + 变量）
     * @return 操作结果
     */
    @PostMapping("/{taskId}/complete")
    @PreAuthorize("hasAuthority('workflow:approval:complete')")
    public R<Void> complete(
            @RequestHeader("X-Tenant-Id") Long tenantId,
            @RequestHeader("X-User-Id") Long userId,
            @PathVariable @NotBlank String taskId,
            @Valid @RequestBody ApprovalRequest request) {
        workflowApprovalService.complete(taskId, request, userId, tenantId);
        return R.ok();
    }

    /**
     * 加签：在当前审批节点动态增加审批人。
     *
     * @param taskId    当前任务 ID
     * @param newUserId 新增审批人用户 ID
     * @return 操作结果
     */
    @PostMapping("/{taskId}/add-signer")
    @PreAuthorize("hasAuthority('workflow:approval:add-signer')")
    public R<Void> addSigner(
            @RequestHeader("X-Tenant-Id") Long tenantId,
            @RequestHeader("X-User-Id") Long userId,
            @PathVariable @NotBlank String taskId,
            @RequestParam @NotBlank String newUserId) {
        workflowApprovalService.addSigner(taskId, newUserId, userId, tenantId);
        return R.ok();
    }

    /**
     * 减签：从当前审批节点移除指定审批人。
     *
     * @param taskId       当前任务 ID
     * @param targetUserId 要移除的审批人用户 ID
     * @return 操作结果
     */
    @PostMapping("/{taskId}/remove-signer")
    @PreAuthorize("hasAuthority('workflow:approval:remove-signer')")
    public R<Void> removeSigner(
            @RequestHeader("X-Tenant-Id") Long tenantId,
            @RequestHeader("X-User-Id") Long userId,
            @PathVariable @NotBlank String taskId,
            @RequestParam @NotBlank String targetUserId) {
        workflowApprovalService.removeSigner(taskId, targetUserId, userId, tenantId);
        return R.ok();
    }

    /**
     * 委托：将任务转交给其他用户审批。
     *
     * @param taskId       当前任务 ID
     * @param targetUserId 被委托人用户 ID
     * @return 操作结果
     */
    @PostMapping("/{taskId}/delegate")
    @PreAuthorize("hasAuthority('workflow:approval:delegate')")
    public R<Void> delegate(
            @RequestHeader("X-Tenant-Id") Long tenantId,
            @RequestHeader("X-User-Id") Long userId,
            @PathVariable @NotBlank String taskId,
            @RequestParam @NotBlank String targetUserId) {
        workflowApprovalService.delegate(taskId, targetUserId, userId, tenantId);
        return R.ok();
    }
}
