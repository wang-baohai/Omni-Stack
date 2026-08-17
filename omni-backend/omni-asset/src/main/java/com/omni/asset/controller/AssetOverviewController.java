package com.omni.asset.controller;

import com.omni.asset.dto.AssetOverviewRequests;
import com.omni.asset.dto.AssetOverviewViews;
import com.omni.asset.security.AssetDataScope;
import com.omni.asset.service.AssetOverviewService;
import com.omni.common.core.result.R;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 资产概览控制器。
 *
 * @author Omni-Stack Team
 */
@Validated
@RestController
@RequestMapping("/api/asset/overview")
@RequiredArgsConstructor
public class AssetOverviewController {

    private final AssetOverviewService overviewService;

    /**
     * 查询管理范围内资产摘要。
     *
     * @return 资产摘要
     */
    @GetMapping("/summary")
    @PreAuthorize("hasAuthority('asset:overview:list')")
    @AssetDataScope(permissionCode = "asset:overview:list")
    public R<AssetOverviewViews.Summary> summary() {
        return R.ok(overviewService.summary());
    }

    /**
     * 查询管理范围内多维资产分布。
     *
     * @param query 查询参数
     * @return 分布行
     */
    @GetMapping("/distribution")
    @PreAuthorize("hasAuthority('asset:overview:list')")
    @AssetDataScope(permissionCode = "asset:overview:list")
    public R<List<AssetOverviewViews.DistributionItem>> distribution(
            @Valid AssetOverviewRequests.DistributionQuery query) {
        return R.ok(overviewService.distribution(query));
    }
}
