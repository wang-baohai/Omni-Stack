package com.omni.srm.service;

import com.omni.srm.dto.SrmViews;

/** SRM 概览统计服务。 */
public interface OverviewService {
    /** 查询概览统计。 */ SrmViews.OverviewSummaryVO summary();
    /** 查询风险看板。 */ SrmViews.RiskDashboardVO riskDashboard();
}
