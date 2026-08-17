package com.omni.procurement.controller;

import com.omni.common.core.result.R;
import com.omni.procurement.dto.OverviewRequests;
import com.omni.procurement.dto.OverviewViews;
import com.omni.procurement.security.ProcDataScope;
import com.omni.procurement.service.OverviewService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 采购概览控制器。
 *
 * @author Omni-Stack Team
 */
@Validated
@RestController
@RequestMapping("/api/procurement/overview")
@RequiredArgsConstructor
public class OverviewController {

    private final OverviewService overviewService;

    /**
     * 查询采购概览摘要。
     *
     * @return 概览摘要
     */
    @GetMapping("/summary")
    @PreAuthorize("hasAuthority('procurement:overview:list')")
    @ProcDataScope(permissionCode = "procurement:overview:list")
    public R<OverviewViews.Summary> summary() {
        return R.ok(overviewService.summary());
    }

    /**
     * 按品类、供应商或负责部门查询采购支出。
     *
     * @param query 查询条件
     * @return 支出分析项
     */
    @GetMapping("/spend-analysis")
    @PreAuthorize("hasAuthority('procurement:overview:list')")
    @ProcDataScope(permissionCode = "procurement:overview:list")
    public R<List<OverviewViews.SpendItem>> spendAnalysis(
            @Valid OverviewRequests.SpendAnalysisQuery query) {
        return R.ok(overviewService.spendAnalysis(query));
    }
}
