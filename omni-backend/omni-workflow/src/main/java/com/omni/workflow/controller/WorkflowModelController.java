package com.omni.workflow.controller;

import com.omni.common.core.result.PageResult;
import com.omni.common.core.result.R;
import com.omni.workflow.dto.*;
import com.omni.workflow.entity.WfProcessModel;
import com.omni.workflow.entity.WfProcessModelVersion;
import com.omni.workflow.service.WorkflowModelService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 流程模型管理控制器。
 * <p>
 * 提供模型的 CRUD、草稿保存、校验、发布、版本管理等接口。
 * 路径前缀：{@code /api/workflow/model}。</p>
 *
 * @author Omni-Stack Team
 */
@RestController
@RequestMapping("/api/workflow/model")
@RequiredArgsConstructor
public class WorkflowModelController {

    private final WorkflowModelService workflowModelService;

    /**
     * 分页查询模型列表。
     *
     * @param tenantId 租户 ID
     * @param keyword  关键字（模型名称/标识，可选）
     * @param category 分类（可选）
     * @param page     页码
     * @param size     每页大小
     * @return 模型分页列表
     */
    @GetMapping("/list")
    @PreAuthorize("hasAuthority('workflow:model:list')")
    public R<PageResult<WfProcessModel>> list(
            @RequestHeader(value = "X-Tenant-Id", defaultValue = "1") Long tenantId,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String category,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int size) {
        return R.ok(workflowModelService.listModels(tenantId, keyword, category, page, size));
    }

    /**
     * 获取单个模型详情。
     *
     * @param id 模型 ID
     * @return 模型实体
     */
    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('workflow:model:list')")
    public R<WfProcessModel> getModel(@PathVariable Long id) {
        return R.ok(workflowModelService.getModel(id));
    }

    /**
     * 创建流程模型。
     *
     * @param tenantId 租户 ID
     * @param userName 操作人
     * @param request  创建请求
     * @return 模型实体
     */
    @PostMapping
    @PreAuthorize("hasAuthority('workflow:model:create')")
    public R<WfProcessModel> create(
            @RequestHeader(value = "X-Tenant-Id", defaultValue = "1") Long tenantId,
            @RequestHeader(value = "X-User-Name", required = false) String userName,
            @Valid @RequestBody CreateModelRequest request) {
        return R.ok(workflowModelService.createModel(request, tenantId, userName));
    }

    /**
     * 保存草稿。
     *
     * @param id       模型 ID
     * @param userName 操作人
     * @param request  草稿请求
     * @return 更新后的草稿版本
     */
    @PutMapping("/{id}/draft")
    @PreAuthorize("hasAuthority('workflow:model:update')")
    public R<WfProcessModelVersion> saveDraft(
            @PathVariable Long id,
            @RequestHeader(value = "X-User-Name", required = false) String userName,
            @Valid @RequestBody SaveDraftRequest request) {
        return R.ok(workflowModelService.saveDraft(id, request, userName));
    }

    /**
     * 校验模型 BPMN XML。
     *
     * @param id 模型 ID
     * @return 校验结果
     */
    @PostMapping("/{id}/validate")
    @PreAuthorize("hasAuthority('workflow:model:validate')")
    public R<ValidateResult> validate(@PathVariable Long id) {
        return R.ok(workflowModelService.validateModel(id));
    }

    /**
     * 发布模型到 Flowable 引擎。
     *
     * @param id       模型 ID
     * @param userName 发布人
     * @return 发布结果
     */
    @PostMapping("/{id}/publish")
    @PreAuthorize("hasAuthority('workflow:model:publish')")
    public R<PublishResult> publish(
            @PathVariable Long id,
            @RequestHeader(value = "X-User-Name", required = false) String userName) {
        return R.ok(workflowModelService.publishModel(id, userName));
    }

    /**
     * 获取模型版本列表。
     *
     * @param id 模型 ID
     * @return 版本列表
     */
    @GetMapping("/{id}/versions")
    @PreAuthorize("hasAuthority('workflow:model:list')")
    public R<List<ModelVersionVO>> listVersions(@PathVariable Long id) {
        return R.ok(workflowModelService.listVersions(id));
    }

    /**
     * 获取指定版本详情（含 BPMN XML 和设计器 JSON）。
     *
     * @param versionId 版本 ID
     * @return 版本实体
     */
    @GetMapping("/version/{versionId}")
    @PreAuthorize("hasAuthority('workflow:model:list')")
    public R<WfProcessModelVersion> getVersion(@PathVariable Long versionId) {
        return R.ok(workflowModelService.getVersion(versionId));
    }

    /**
     * 删除模型。
     *
     * @param id 模型 ID
     * @return 操作结果
     */
    @DeleteMapping("/{id}")
    @PreAuthorize("hasAuthority('workflow:model:delete')")
    public R<Void> deleteModel(@PathVariable Long id) {
        workflowModelService.deleteModel(id);
        return R.ok();
    }
}
