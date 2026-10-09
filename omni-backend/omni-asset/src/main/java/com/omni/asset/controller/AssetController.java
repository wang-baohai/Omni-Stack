package com.omni.asset.controller;

import com.omni.asset.dto.AssetRequests;
import com.omni.asset.dto.AssetViews;
import com.omni.asset.entity.AstAsset;
import com.omni.common.service.datascope.ServiceDataScope;
import com.omni.asset.service.AssetService;
import com.omni.common.core.operlog.OperLog;
import com.omni.common.core.operlog.OperType;
import com.omni.common.core.result.PageResult;
import com.omni.common.core.result.R;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Positive;
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
 * 资产台账控制器。
 *
 * @author Omni-Stack Team
 */
@Validated
@RestController
@RequestMapping("/api/asset/asset")
@RequiredArgsConstructor
public class AssetController {

    private final AssetService assetService;

    /**
     * 按管理归属分页查询资产。
     *
     * @param query 查询条件
     * @return 资产分页
     */
    @GetMapping("/list")
    @PreAuthorize("hasAuthority('asset:asset:list')")
    @ServiceDataScope(permissionCode = "asset:asset:list")
    public R<PageResult<AssetViews.AssetVO>> list(@Valid AssetRequests.AssetQuery query) {
        return R.ok(assetService.page(query));
    }

    /**
     * 固定按当前使用人查询“我的资产”。
     *
     * @param query 查询条件
     * @return 当前用户资产分页
     */
    @GetMapping("/my")
    @PreAuthorize("hasAuthority('asset:asset:self')")
    @ServiceDataScope(permissionCode = "asset:asset:self")
    public R<PageResult<AssetViews.AssetVO>> my(@Valid AssetRequests.MyAssetQuery query) {
        return R.ok(assetService.pageMine(query));
    }

    /**
     * 查询管理范围内资产详情。
     *
     * @param id 资产 ID
     * @return 资产详情
     */
    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('asset:asset:list')")
    @ServiceDataScope(permissionCode = "asset:asset:list")
    public R<AssetViews.AssetVO> get(@PathVariable @Positive Long id) {
        return R.ok(assetService.get(id));
    }

    /**
     * 查询资产变更历史。
     *
     * @param id 资产 ID
     * @param query 分页参数
     * @return 历史分页
     */
    @GetMapping("/{id}/history")
    @PreAuthorize("hasAuthority('asset:asset:list')")
    @ServiceDataScope(permissionCode = "asset:asset:list")
    public R<PageResult<AssetViews.HistoryVO>> history(
            @PathVariable @Positive Long id, @Valid AssetRequests.HistoryQuery query) {
        return R.ok(assetService.history(id, query));
    }

    /**
     * 手工创建在库资产。
     *
     * @param request 创建请求
     * @return 新资产
     */
    @PostMapping
    @PreAuthorize("hasAuthority('asset:asset:create')")
    @ServiceDataScope(permissionCode = "asset:asset:create")
    @OperLog(module = "资产台账", operType = OperType.CREATE,
            entityClass = AstAsset.class, idExpr = "#result.data.id")
    public R<AssetViews.AssetVO> create(@Valid @RequestBody AssetRequests.CreateAssetRequest request) {
        return R.ok(assetService.create(request));
    }

    /**
     * 更新资产基础资料。
     *
     * @param id 资产 ID
     * @param request 更新请求
     * @return 更新后资产
     */
    @PutMapping("/{id}")
    @PreAuthorize("hasAuthority('asset:asset:update')")
    @ServiceDataScope(permissionCode = "asset:asset:update")
    @OperLog(module = "资产台账", operType = OperType.UPDATE,
            entityClass = AstAsset.class, idExpr = "#id")
    public R<AssetViews.AssetVO> update(
            @PathVariable @Positive Long id,
            @Valid @RequestBody AssetRequests.UpdateAssetRequest request) {
        return R.ok(assetService.update(id, request));
    }

    /**
     * 删除未发生业务动作的手工在库资产。
     *
     * @param id 资产 ID
     * @param version 乐观锁版本
     * @return 空成功响应
     */
    @DeleteMapping("/{id}")
    @PreAuthorize("hasAuthority('asset:asset:delete')")
    @ServiceDataScope(permissionCode = "asset:asset:delete")
    @OperLog(module = "资产台账", operType = OperType.DELETE,
            entityClass = AstAsset.class, idExpr = "#id")
    public R<Void> delete(
            @PathVariable @Positive Long id,
            @RequestParam @Min(value = 0, message = "乐观锁版本不能小于 0") Integer version) {
        assetService.delete(id, version);
        return R.ok();
    }

    /**
     * 分配在库资产。
     *
     * @param id 资产 ID
     * @param request 分配请求
     * @return 更新后资产
     */
    @PostMapping("/{id}/allocate")
    @PreAuthorize("hasAuthority('asset:asset:allocate')")
    @ServiceDataScope(permissionCode = "asset:asset:allocate")
    @OperLog(module = "资产台账", operType = OperType.UPDATE,
            entityClass = AstAsset.class, idExpr = "#id")
    public R<AssetViews.AssetVO> allocate(
            @PathVariable @Positive Long id,
            @Valid @RequestBody AssetRequests.AllocateRequest request) {
        return R.ok(assetService.allocate(id, request));
    }

    /**
     * 当前使用人确认领用。
     *
     * @param id 资产 ID
     * @param request 版本命令
     * @return 更新后资产
     */
    @PostMapping("/{id}/accept")
    @PreAuthorize("hasAuthority('asset:asset:accept')")
    @ServiceDataScope(permissionCode = "asset:asset:accept")
    @OperLog(module = "资产台账", operType = OperType.UPDATE,
            entityClass = AstAsset.class, idExpr = "#id")
    public R<AssetViews.AssetVO> accept(
            @PathVariable @Positive Long id,
            @Valid @RequestBody AssetRequests.VersionCommandRequest request) {
        return R.ok(assetService.accept(id, request));
    }

    /**
     * 当前使用人退还资产。
     *
     * @param id 资产 ID
     * @param request 版本命令
     * @return 更新后资产
     */
    @PostMapping("/{id}/return")
    @PreAuthorize("hasAuthority('asset:asset:return')")
    @ServiceDataScope(permissionCode = "asset:asset:return")
    @OperLog(module = "资产台账", operType = OperType.UPDATE,
            entityClass = AstAsset.class, idExpr = "#id")
    public R<AssetViews.AssetVO> returnAsset(
            @PathVariable @Positive Long id,
            @Valid @RequestBody AssetRequests.VersionCommandRequest request) {
        return R.ok(assetService.returnAsset(id, request));
    }

    /**
     * 将使用中资产标记为维修中。
     *
     * @param id 资产 ID
     * @param request 版本命令
     * @return 更新后资产
     */
    @PostMapping("/{id}/maintenance/start")
    @PreAuthorize("hasAuthority('asset:asset:maintenance')")
    @ServiceDataScope(permissionCode = "asset:asset:maintenance")
    @OperLog(module = "资产台账", operType = OperType.UPDATE,
            entityClass = AstAsset.class, idExpr = "#id")
    public R<AssetViews.AssetVO> startMaintenance(
            @PathVariable @Positive Long id,
            @Valid @RequestBody AssetRequests.VersionCommandRequest request) {
        return R.ok(assetService.startMaintenance(id, request));
    }

    /**
     * 完成维修并恢复使用中状态。
     *
     * @param id 资产 ID
     * @param request 版本命令
     * @return 更新后资产
     */
    @PostMapping("/{id}/maintenance/complete")
    @PreAuthorize("hasAuthority('asset:asset:maintenance')")
    @ServiceDataScope(permissionCode = "asset:asset:maintenance")
    @OperLog(module = "资产台账", operType = OperType.UPDATE,
            entityClass = AstAsset.class, idExpr = "#id")
    public R<AssetViews.AssetVO> completeMaintenance(
            @PathVariable @Positive Long id,
            @Valid @RequestBody AssetRequests.VersionCommandRequest request) {
        return R.ok(assetService.completeMaintenance(id, request));
    }
}
