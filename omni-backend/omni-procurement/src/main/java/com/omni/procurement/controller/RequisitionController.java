package com.omni.procurement.controller;

import com.omni.common.core.operlog.OperLog;
import com.omni.common.core.operlog.OperType;
import com.omni.common.core.result.PageResult;
import com.omni.common.core.result.R;
import com.omni.procurement.dto.RequisitionRequests;
import com.omni.procurement.dto.RequisitionViews;
import com.omni.procurement.entity.ProcRequisition;
import com.omni.common.service.datascope.ServiceDataScope;
import com.omni.procurement.service.RequisitionService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
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
 * 请购申请控制器。
 *
 * @author Omni-Stack Team
 */
@Validated
@RestController
@RequestMapping("/api/procurement/requisition")
@RequiredArgsConstructor
public class RequisitionController {

    private final RequisitionService requisitionService;

    /**
     * 分页查询请购。
     *
     * @param query 查询条件
     * @return 请购分页
     */
    @GetMapping("/list")
    @PreAuthorize("hasAuthority('procurement:requisition:list')")
    @ServiceDataScope(permissionCode = "procurement:requisition:list")
    public R<PageResult<RequisitionViews.Summary>> list(@Valid RequisitionRequests.Query query) {
        return R.ok(requisitionService.page(query));
    }

    /**
     * 查询普通请购详情。
     *
     * @param id 请购 ID
     * @return 请购详情
     */
    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('procurement:requisition:list')")
    @ServiceDataScope(permissionCode = "procurement:requisition:list")
    public R<RequisitionViews.Detail> get(@PathVariable Long id) {
        return R.ok(requisitionService.get(id));
    }

    /**
     * 校验 Workflow 任务分配后查询审批专用业务视图。
     *
     * @param id 请购 ID
     * @param taskId Workflow 任务 ID
     * @return 审批业务视图
     */
    @GetMapping("/{id}/approval-view")
    @PreAuthorize("hasAuthority('procurement:requisition:approve')")
    public R<RequisitionViews.ApprovalView> approvalView(
            @PathVariable Long id,
            @RequestParam @NotBlank @Size(max = 64) String taskId) {
        return R.ok(requisitionService.approvalView(id, taskId));
    }

    /**
     * 创建请购草稿。
     *
     * @param request 创建请求
     * @return 请购详情
     */
    @PostMapping
    @PreAuthorize("hasAuthority('procurement:requisition:create')")
    @ServiceDataScope(permissionCode = "procurement:requisition:create")
    @OperLog(module = "采购请购", operType = OperType.CREATE,
            entityClass = ProcRequisition.class, idExpr = "#result.data.id")
    public R<RequisitionViews.Detail> create(
            @Valid @RequestBody RequisitionRequests.CreateRequest request) {
        return R.ok(requisitionService.create(request));
    }

    /**
     * 更新请购内容。
     *
     * @param id 请购 ID
     * @param request 更新请求
     * @return 请购详情
     */
    @PutMapping("/{id}")
    @PreAuthorize("hasAuthority('procurement:requisition:update')")
    @ServiceDataScope(permissionCode = "procurement:requisition:update")
    @OperLog(module = "采购请购", operType = OperType.UPDATE,
            entityClass = ProcRequisition.class, idExpr = "#id")
    public R<RequisitionViews.Detail> update(
            @PathVariable Long id, @Valid @RequestBody RequisitionRequests.UpdateRequest request) {
        return R.ok(requisitionService.update(id, request));
    }

    /**
     * 删除请购草稿。
     *
     * @param id 请购 ID
     * @param version 乐观锁版本
     * @return 空成功响应
     */
    @DeleteMapping("/{id}")
    @PreAuthorize("hasAuthority('procurement:requisition:delete')")
    @ServiceDataScope(permissionCode = "procurement:requisition:delete")
    @OperLog(module = "采购请购", operType = OperType.DELETE,
            entityClass = ProcRequisition.class, idExpr = "#id")
    public R<Void> delete(
            @PathVariable Long id,
            @RequestParam @Min(value = 0, message = "乐观锁版本不能小于 0") Integer version) {
        requisitionService.delete(id, version);
        return R.ok();
    }

    /**
     * 提交请购并启动审批。
     *
     * @param id 请购 ID
     * @param command 乐观锁命令
     * @return 审批中的请购详情
     */
    @PostMapping("/{id}/submit")
    @PreAuthorize("hasAuthority('procurement:requisition:submit')")
    @ServiceDataScope(permissionCode = "procurement:requisition:submit")
    @OperLog(module = "采购请购", operType = OperType.UPDATE,
            entityClass = ProcRequisition.class, idExpr = "#id")
    public R<RequisitionViews.Detail> submit(
            @PathVariable Long id,
            @Valid @RequestBody RequisitionRequests.VersionCommand command) {
        return R.ok(requisitionService.submit(id, command.getVersion()));
    }

    /**
     * 使用同一幂等快照重试 Workflow 启动。
     *
     * @param id 请购 ID
     * @param command 乐观锁命令
     * @return 审批中的请购详情
     */
    @PostMapping("/{id}/retry-start")
    @PreAuthorize("hasAuthority('procurement:requisition:submit')")
    @ServiceDataScope(permissionCode = "procurement:requisition:submit")
    @OperLog(module = "采购请购", operType = OperType.UPDATE,
            entityClass = ProcRequisition.class, idExpr = "#id")
    public R<RequisitionViews.Detail> retryStart(
            @PathVariable Long id,
            @Valid @RequestBody RequisitionRequests.VersionCommand command) {
        return R.ok(requisitionService.retryStart(id, command.getVersion()));
    }

    /**
     * 取消草稿或 Workflow 启动失败的请购。
     *
     * @param id 请购 ID
     * @param command 乐观锁命令
     * @return 取消后的请购详情
     */
    @PostMapping("/{id}/cancel")
    @PreAuthorize("hasAuthority('procurement:requisition:cancel')")
    @ServiceDataScope(permissionCode = "procurement:requisition:cancel")
    @OperLog(module = "采购请购", operType = OperType.UPDATE,
            entityClass = ProcRequisition.class, idExpr = "#id")
    public R<RequisitionViews.Detail> cancel(
            @PathVariable Long id,
            @Valid @RequestBody RequisitionRequests.VersionCommand command) {
        return R.ok(requisitionService.cancel(id, command.getVersion()));
    }
}
