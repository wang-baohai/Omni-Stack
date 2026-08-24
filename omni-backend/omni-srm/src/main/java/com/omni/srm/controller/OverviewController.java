package com.omni.srm.controller;

import com.omni.common.core.result.R;
import com.omni.srm.dto.SrmViews;
import com.omni.common.service.datascope.ServiceDataScope;
import com.omni.srm.service.OverviewService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** SRM 概览控制器。 */
@RestController
@RequestMapping("/api/srm/overview")
@RequiredArgsConstructor
public class OverviewController {
    private final OverviewService overviewService;

    /** 查询概览统计。 */
    @GetMapping("/summary") @PreAuthorize("hasAuthority('srm:overview:list')") @ServiceDataScope(permissionCode = "srm:overview:list")
    public R<SrmViews.OverviewSummaryVO> summary() {
        return R.ok(overviewService.summary());
    }

    /** 查询风险看板。 */
    @GetMapping("/risk-dashboard") @PreAuthorize("hasAuthority('srm:overview:list')")
    @ServiceDataScope(permissionCode = "srm:overview:list")
    public R<SrmViews.RiskDashboardVO> riskDashboard() {
        return R.ok(overviewService.riskDashboard());
    }
}
