package com.omni.asset.controller;

import com.omni.asset.dto.AssetOperationRequests;
import com.omni.asset.dto.AssetOperationViews;
import com.omni.asset.entity.AstDisposal;
import com.omni.common.service.datascope.ServiceDataScope;
import com.omni.asset.service.AssetDisposalService;
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
 * 资产处置控制器。
 *
 * @author Omni-Stack Team
 */
@Validated
@RestController
@RequestMapping("/api/asset/disposal")
@RequiredArgsConstructor
public class AssetDisposalController {

    private final AssetDisposalService disposalService;

    /**
     * 分页查询处置申请。
     *
     * @param query 查询条件
     * @return 处置分页
     */
    @GetMapping("/list")
    @PreAuthorize("hasAuthority('asset:disposal:list')")
    @ServiceDataScope(permissionCode = "asset:disposal:list")
    public R<PageResult<AssetOperationViews.DisposalVO>> list(
            @Valid AssetOperationRequests.DisposalQuery query) {
        return R.ok(disposalService.page(query));
    }

    /**
     * 查询处置详情。
     *
     * @param id 申请 ID
     * @return 处置详情
     */
    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('asset:disposal:list')")
    @ServiceDataScope(permissionCode = "asset:disposal:list")
    public R<AssetOperationViews.DisposalVO> get(@PathVariable @Positive Long id) {
        return R.ok(disposalService.get(id));
    }

    /**
     * 校验 Workflow 任务后读取审批视图。
     *
     * @param id 申请 ID
     * @param taskId Workflow 任务 ID
     * @return 处置审批视图
     */
    @GetMapping("/{id}/approval-view")
    @PreAuthorize("hasAuthority('asset:disposal:approve')")
    @ServiceDataScope(permissionCode = "asset:disposal:approve")
    public R<AssetOperationViews.DisposalVO> approvalView(
            @PathVariable @Positive Long id,
            @RequestParam @NotBlank @Size(max = 64) String taskId) {
        return R.ok(disposalService.approvalView(id, taskId));
    }

    /**
     * 创建处置申请并启动审批。
     *
     * @param request 创建请求
     * @return 处置详情
     */
    @PostMapping
    @PreAuthorize("hasAuthority('asset:disposal:create')")
    @ServiceDataScope(permissionCode = "asset:disposal:create")
    @OperLog(module = "资产处置", operType = OperType.CREATE,
            entityClass = AstDisposal.class, idExpr = "#result.data.id")
    public R<AssetOperationViews.DisposalVO> create(
            @Valid @RequestBody AssetOperationRequests.CreateDisposalRequest request) {
        return R.ok(disposalService.create(request));
    }

    /**
     * 重试启动 Workflow。
     *
     * @param id 申请 ID
     * @param command 版本命令
     * @return 处置详情
     */
    @PostMapping("/{id}/retry-start")
    @PreAuthorize("hasAuthority('asset:disposal:retry')")
    @ServiceDataScope(permissionCode = "asset:disposal:retry")
    @OperLog(module = "资产处置", operType = OperType.UPDATE,
            entityClass = AstDisposal.class, idExpr = "#id")
    public R<AssetOperationViews.DisposalVO> retryStart(
            @PathVariable @Positive Long id,
            @Valid @RequestBody AssetOperationRequests.VersionCommand command) {
        return R.ok(disposalService.retryStart(id, command.getVersion()));
    }

    /**
     * 取消 Workflow 明确启动失败的处置。
     *
     * @param id 申请 ID
     * @param command 版本命令
     * @return 处置详情
     */
    @PostMapping("/{id}/cancel")
    @PreAuthorize("hasAuthority('asset:disposal:cancel')")
    @ServiceDataScope(permissionCode = "asset:disposal:cancel")
    @OperLog(module = "资产处置", operType = OperType.UPDATE,
            entityClass = AstDisposal.class, idExpr = "#id")
    public R<AssetOperationViews.DisposalVO> cancel(
            @PathVariable @Positive Long id,
            @Valid @RequestBody AssetOperationRequests.VersionCommand command) {
        return R.ok(disposalService.cancel(id, command.getVersion()));
    }

    /**
     * 完成审批通过后的实物处置。
     *
     * @param id 申请 ID
     * @param command 版本命令
     * @return 处置详情
     */
    @PostMapping("/{id}/complete")
    @PreAuthorize("hasAuthority('asset:disposal:complete')")
    @ServiceDataScope(permissionCode = "asset:disposal:complete")
    @OperLog(module = "资产处置", operType = OperType.UPDATE,
            entityClass = AstDisposal.class, idExpr = "#id")
    public R<AssetOperationViews.DisposalVO> complete(
            @PathVariable @Positive Long id,
            @Valid @RequestBody AssetOperationRequests.VersionCommand command) {
        return R.ok(disposalService.complete(id, command.getVersion()));
    }
}
