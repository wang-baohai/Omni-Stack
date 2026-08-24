package com.omni.asset.controller;

import com.omni.asset.dto.AssetViews;
import com.omni.common.service.datascope.ServiceDataScope;
import com.omni.asset.service.AssetOptionService;
import com.omni.common.core.result.R;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/** 资产操作候选项控制器。 */
@RestController
@RequestMapping("/api/asset/options")
@RequiredArgsConstructor
public class AssetOptionsController {

    private final AssetOptionService assetOptionService;

    /** 查询资产管理员或使用人候选。 */
    @GetMapping("/users")
    @PreAuthorize("hasAnyAuthority('asset:asset:list','asset:asset:create',"
            + "'asset:asset:update','asset:asset:allocate','asset:transfer:create',"
            + "'asset:disposal:create')")
    public R<List<AssetViews.UserOptionVO>> users(
            @RequestParam(required = false) String keyword,
            @RequestParam(defaultValue = "20") int limit) {
        return R.ok(assetOptionService.listUsers(keyword, limit));
    }

    /** 查询当前数据范围内可发起调拨的资产候选。 */
    @GetMapping("/transfer-assets")
    @PreAuthorize("hasAuthority('asset:transfer:create')")
    @ServiceDataScope(permissionCode = "asset:transfer:create")
    public R<List<AssetViews.AssetOptionVO>> transferAssets(
            @RequestParam(required = false) String keyword,
            @RequestParam(defaultValue = "30") int limit) {
        return R.ok(assetOptionService.listEligibleAssets(keyword, limit));
    }

    /** 查询当前数据范围内可发起处置的资产候选。 */
    @GetMapping("/disposal-assets")
    @PreAuthorize("hasAuthority('asset:disposal:create')")
    @ServiceDataScope(permissionCode = "asset:disposal:create")
    public R<List<AssetViews.AssetOptionVO>> disposalAssets(
            @RequestParam(required = false) String keyword,
            @RequestParam(defaultValue = "30") int limit) {
        return R.ok(assetOptionService.listEligibleAssets(keyword, limit));
    }

    /** 查询当前租户已批准的供应商候选。 */
    @GetMapping("/suppliers")
    @PreAuthorize("hasAnyAuthority('asset:asset:create','asset:asset:update')")
    public R<List<AssetViews.SupplierOptionVO>> suppliers(
            @RequestParam(required = false) String keyword,
            @RequestParam(defaultValue = "30") int limit) {
        return R.ok(assetOptionService.listSuppliers(keyword, limit));
    }
}
