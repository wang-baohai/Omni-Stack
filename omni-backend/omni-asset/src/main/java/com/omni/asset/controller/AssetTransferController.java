package com.omni.asset.controller;

import com.omni.asset.dto.AssetOperationRequests;
import com.omni.asset.dto.AssetOperationViews;
import com.omni.asset.entity.AstTransfer;
import com.omni.asset.security.AssetDataScope;
import com.omni.asset.service.AssetTransferService;
import com.omni.common.core.operlog.OperLog;
import com.omni.common.core.operlog.OperType;
import com.omni.common.core.result.PageResult;
import com.omni.common.core.result.R;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 资产调拨控制器。
 *
 * @author Omni-Stack Team
 */
@Validated
@RestController
@RequestMapping("/api/asset/transfer")
@RequiredArgsConstructor
public class AssetTransferController {

    private final AssetTransferService transferService;

    /**
     * 分页查询调拨申请。
     *
     * @param query 查询条件
     * @return 调拨分页
     */
    @GetMapping("/list")
    @PreAuthorize("hasAuthority('asset:transfer:list')")
    @AssetDataScope(permissionCode = "asset:transfer:list")
    public R<PageResult<AssetOperationViews.TransferVO>> list(
            @Valid AssetOperationRequests.TransferQuery query) {
        return R.ok(transferService.page(query));
    }

    /**
     * 查询调拨详情。
     *
     * @param id 申请 ID
     * @return 调拨详情
     */
    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('asset:transfer:list')")
    @AssetDataScope(permissionCode = "asset:transfer:list")
    public R<AssetOperationViews.TransferVO> get(@PathVariable @Positive Long id) {
        return R.ok(transferService.get(id));
    }

    /**
     * 校验 Workflow 任务后读取审批视图。
     *
     * @param id 申请 ID
     * @param taskId Workflow 任务 ID
     * @return 调拨审批视图
     */
    @GetMapping("/{id}/approval-view")
    @PreAuthorize("hasAuthority('asset:transfer:approve')")
    @AssetDataScope(permissionCode = "asset:transfer:approve")
    public R<AssetOperationViews.TransferVO> approvalView(
            @PathVariable @Positive Long id,
            @RequestParam @NotBlank @Size(max = 64) String taskId) {
        return R.ok(transferService.approvalView(id, taskId));
    }

    /**
     * 创建调拨申请并启动审批。
     *
     * @param request 创建请求
     * @return 调拨详情
     */
    @PostMapping
    @PreAuthorize("hasAuthority('asset:transfer:create')")
    @AssetDataScope(permissionCode = "asset:transfer:create")
    @OperLog(module = "资产调拨", operType = OperType.CREATE,
            entityClass = AstTransfer.class, idExpr = "#result.data.id")
    public R<AssetOperationViews.TransferVO> create(
            @Valid @RequestBody AssetOperationRequests.CreateTransferRequest request) {
        return R.ok(transferService.create(request));
    }

    /**
     * 重试启动 Workflow。
     *
     * @param id 申请 ID
     * @param command 版本命令
     * @return 调拨详情
     */
    @PostMapping("/{id}/retry-start")
    @PreAuthorize("hasAuthority('asset:transfer:retry')")
    @AssetDataScope(permissionCode = "asset:transfer:retry")
    @OperLog(module = "资产调拨", operType = OperType.UPDATE,
            entityClass = AstTransfer.class, idExpr = "#id")
    public R<AssetOperationViews.TransferVO> retryStart(
            @PathVariable @Positive Long id,
            @Valid @RequestBody AssetOperationRequests.VersionCommand command) {
        return R.ok(transferService.retryStart(id, command.getVersion()));
    }

    /**
     * 取消 Workflow 明确启动失败的调拨。
     *
     * @param id 申请 ID
     * @param command 版本命令
     * @return 调拨详情
     */
    @PostMapping("/{id}/cancel")
    @PreAuthorize("hasAuthority('asset:transfer:cancel')")
    @AssetDataScope(permissionCode = "asset:transfer:cancel")
    @OperLog(module = "资产调拨", operType = OperType.UPDATE,
            entityClass = AstTransfer.class, idExpr = "#id")
    public R<AssetOperationViews.TransferVO> cancel(
            @PathVariable @Positive Long id,
            @Valid @RequestBody AssetOperationRequests.VersionCommand command) {
        return R.ok(transferService.cancel(id, command.getVersion()));
    }

    /**
     * 完成审批通过后的资产交接。
     *
     * @param id 申请 ID
     * @param command 版本命令
     * @return 调拨详情
     */
    @PostMapping("/{id}/complete")
    @PreAuthorize("hasAuthority('asset:transfer:complete')")
    @AssetDataScope(permissionCode = "asset:transfer:complete")
    @OperLog(module = "资产调拨", operType = OperType.UPDATE,
            entityClass = AstTransfer.class, idExpr = "#id")
    public R<AssetOperationViews.TransferVO> complete(
            @PathVariable @Positive Long id,
            @Valid @RequestBody AssetOperationRequests.VersionCommand command) {
        return R.ok(transferService.complete(id, command.getVersion()));
    }
}
