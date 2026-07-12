package com.omni.workflow.controller;

import com.omni.common.core.result.PageResult;
import com.omni.common.core.result.R;
import com.omni.workflow.dto.ApprovalRecord;
import com.omni.workflow.dto.ProcessProgressResponse;
import com.omni.workflow.dto.StartProcessRequest;
import com.omni.workflow.entity.WfProcessInstanceExt;
import com.omni.workflow.service.ProcessInstanceService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 流程实例控制器。
 * <p>提供流程发起、我发起的、我已办的、管理员查询等接口。</p>
 *
 * @author Omni-Stack Team
 */
@RestController
@RequestMapping("/api/workflow/process-instance")
@RequiredArgsConstructor
public class ProcessInstanceController {

    private final ProcessInstanceService processInstanceService;

    /**
     * 发起流程实例。
     *
     * @param tenantId 租户 ID
     * @param userId   用户 ID
     * @param userName 用户名
     * @param request  发起请求
     * @return 流程实例 ID
     */
    @PostMapping("/start")
    @PreAuthorize("hasAuthority('workflow:instance:start')")
    public R<String> start(
            @RequestHeader("X-Tenant-Id") Long tenantId,
            @RequestHeader("X-User-Id") Long userId,
            @RequestHeader(value = "X-User-Name", required = false) String userName,
            @Valid @RequestBody StartProcessRequest request) {
        return R.ok(processInstanceService.start(request, userId, userName, tenantId));
    }

    /**
     * 查询"我发起的"流程实例。
     *
     * @param tenantId 租户 ID
     * @param userId   用户 ID
     * @param title    流程标题（可选）
     * @param status   状态（可选）
     * @param page     页码
     * @param size     每页大小
     * @return 分页结果
     */
    @GetMapping("/my-initiated")
    public R<PageResult<WfProcessInstanceExt>> myInitiated(
            @RequestHeader("X-Tenant-Id") Long tenantId,
            @RequestHeader("X-User-Id") Long userId,
            @RequestParam(required = false) String title,
            @RequestParam(required = false) Integer status,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int size) {
        return R.ok(processInstanceService.myInitiated(userId, tenantId, title, status, page, size));
    }

    /**
     * 查询"我已办的"流程实例。
     *
     * @param tenantId 租户 ID
     * @param userId   用户 ID
     * @param title    流程标题（可选）
     * @param page     页码
     * @param size     每页大小
     * @return 分页结果
     */
    @GetMapping("/my-completed")
    public R<PageResult<WfProcessInstanceExt>> myCompleted(
            @RequestHeader("X-Tenant-Id") Long tenantId,
            @RequestHeader("X-User-Id") Long userId,
            @RequestParam(required = false) String title,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int size) {
        return R.ok(processInstanceService.myCompleted(userId, tenantId, title, page, size));
    }

    /**
     * 终止流程实例（仅发起人可操作）。
     *
     * @param processInstanceId 流程实例 ID
     * @param reason            终止原因
     * @return 操作结果
     */
    @PutMapping("/{processInstanceId}/terminate")
    @PreAuthorize("hasAuthority('workflow:instance:terminate')")
    public R<Void> terminate(
            @RequestHeader("X-Tenant-Id") Long tenantId,
            @RequestHeader("X-User-Id") Long userId,
            @PathVariable String processInstanceId,
            @RequestParam(required = false) String reason) {
        processInstanceService.terminate(processInstanceId, reason, userId, tenantId);
        return R.ok();
    }

    /**
     * 管理员查询所有流程实例。
     *
     * @param tenantId 租户 ID
     * @param title    流程标题（可选）
     * @param status   状态（可选）
     * @param page     页码
     * @param size     每页大小
     * @return 分页结果
     */
    @GetMapping("/list")
    @PreAuthorize("hasAuthority('workflow:instance:list')")
    public R<PageResult<WfProcessInstanceExt>> list(
            @RequestHeader("X-Tenant-Id") Long tenantId,
            @RequestParam(required = false) String title,
            @RequestParam(required = false) Integer status,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int size) {
        return R.ok(processInstanceService.listAll(tenantId, title, status, page, size));
    }

    /**
     * 获取流程实例的流转进度。
     *
     * @param processInstanceId 流程实例 ID
     * @return 进度信息（已完成/进行中/未到达活动节点）
     */
    @GetMapping("/{processInstanceId}/progress")
    public R<ProcessProgressResponse> getProgress(
            @PathVariable String processInstanceId) {
        return R.ok(processInstanceService.getProgress(processInstanceId));
    }

    /**
     * 获取流程实例的审批记录。
     * <p>逐人展示每个 userTask 节点的审批结果和意见。</p>
     *
     * @param processInstanceId 流程实例 ID
     * @return 审批记录列表
     */
    @GetMapping("/{processInstanceId}/approval-records")
    public R<List<ApprovalRecord>> getApprovalRecords(
            @PathVariable String processInstanceId) {
        return R.ok(processInstanceService.getApprovalRecords(processInstanceId));
    }
}
