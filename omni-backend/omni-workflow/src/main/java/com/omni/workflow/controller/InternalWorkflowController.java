package com.omni.workflow.controller;

import com.omni.common.core.result.R;
import com.omni.workflow.dto.internal.InternalApprovalPreviewResponse;
import com.omni.workflow.dto.internal.InternalModelVersionResolveRequest;
import com.omni.workflow.dto.internal.InternalModelVersionResponse;
import com.omni.workflow.dto.internal.InternalPublishedModelVersionResponse;
import com.omni.workflow.dto.internal.InternalStartProcessRequest;
import com.omni.workflow.dto.internal.InternalStartProcessResponse;
import com.omni.workflow.dto.internal.InternalTaskAssignmentRequest;
import com.omni.workflow.dto.internal.InternalTaskAssignmentResponse;
import com.omni.workflow.service.InternalWorkflowService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.RequiredArgsConstructor;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Workflow 内部服务控制器。
 *
 * @author Omni-Stack Team
 */
@RestController
@RequestMapping("/api/internal/workflow")
@RequiredArgsConstructor
@Validated
public class InternalWorkflowController {

    private final InternalWorkflowService internalWorkflowService;

    /**
     * 查询并校验已发布的流程模型版本。
     *
     * @param tenantId       请求头租户 ID
     * @param modelVersionId 流程模型版本 ID
     * @return 已发布的流程模型版本
     */
    @GetMapping("/model-version/{modelVersionId}")
    public R<InternalModelVersionResponse> getModelVersion(
            @RequestHeader("X-Tenant-Id")
            @Positive(message = "租户 ID 必须为正整数") Long tenantId,
            @PathVariable
            @Positive(message = "流程模型版本 ID 必须为正整数") Long modelVersionId) {
        return R.ok(internalWorkflowService.getModelVersion(tenantId, modelVersionId));
    }

    /**
     * 按业务分类查询当前已发布模型版本。
     *
     * @param tenantId 请求头租户 ID
     * @param category 业务分类
     * @return 当前已发布模型版本
     */
    @GetMapping("/model-version/current")
    public R<InternalModelVersionResponse> getCurrentPublishedModelVersion(
            @RequestHeader("X-Tenant-Id")
            @Positive(message = "租户 ID 必须为正整数") Long tenantId,
            @RequestParam
            @NotBlank(message = "业务分类不能为空") String category) {
        return R.ok(internalWorkflowService.getCurrentPublishedModelVersion(tenantId, category));
    }

    /**
     * 查询指定分类的全部当前已发布模型版本。
     *
     * @param tenantId 请求头租户 ID
     * @param category 模型分类
     * @return 当前已发布模型版本列表
     */
    @GetMapping("/model-versions/published")
    public R<List<InternalPublishedModelVersionResponse>> listPublishedModelVersions(
            @RequestHeader("X-Tenant-Id")
            @Positive(message = "租户 ID 必须为正整数") Long tenantId,
            @RequestParam
            @NotBlank(message = "业务分类不能为空")
            @Size(max = 100, message = "业务分类长度不能超过 100") String category) {
        return R.ok(internalWorkflowService.listPublishedModelVersions(tenantId, category));
    }

    /**
     * 批量解析模型版本元数据与可用状态。
     *
     * @param tenantId 请求头租户 ID
     * @param request 模型版本 ID 请求
     * @return 解析结果
     */
    @PostMapping("/model-versions/resolve")
    public R<List<InternalPublishedModelVersionResponse>> resolveModelVersions(
            @RequestHeader("X-Tenant-Id")
            @Positive(message = "租户 ID 必须为正整数") Long tenantId,
            @Valid @RequestBody InternalModelVersionResolveRequest request) {
        return R.ok(internalWorkflowService.resolveModelVersions(
                tenantId, request.getModelVersionIds()));
    }

    /**
     * 返回不含原始 BPMN 的安全审批图预览。
     *
     * @param tenantId 请求头租户 ID
     * @param modelVersionId 模型版本 ID
     * @return 安全审批图预览
     */
    @GetMapping("/model-version/{modelVersionId}/preview")
    public R<InternalApprovalPreviewResponse> previewModelVersion(
            @RequestHeader("X-Tenant-Id")
            @Positive(message = "租户 ID 必须为正整数") Long tenantId,
            @PathVariable
            @Positive(message = "流程模型版本 ID 必须为正整数") Long modelVersionId) {
        return R.ok(internalWorkflowService.previewModelVersion(tenantId, modelVersionId));
    }

    /**
     * 供内部业务服务发起流程。
     *
     * @param tenantId 请求头租户 ID
     * @param request  发起流程请求
     * @return 发起结果
     */
    @PostMapping("/process-instance/start")
    public R<InternalStartProcessResponse> start(
            @RequestHeader("X-Tenant-Id") Long tenantId,
            @Valid @RequestBody InternalStartProcessRequest request) {
        return R.ok(internalWorkflowService.start(tenantId, request));
    }

    /**
     * 校验当前业务用户是否具备任务处理资格。
     *
     * @param tenantId 请求头租户 ID
     * @param request  任务校验请求
     * @return 校验结果
     */
    @PostMapping("/task/assignment/validate")
    public R<InternalTaskAssignmentResponse> validateAssignment(
            @RequestHeader("X-Tenant-Id") Long tenantId,
            @Valid @RequestBody InternalTaskAssignmentRequest request) {
        return R.ok(internalWorkflowService.validateAssignment(tenantId, request));
    }
}
