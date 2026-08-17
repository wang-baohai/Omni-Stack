package com.omni.procurement.service;

import com.omni.procurement.dto.OverviewRequests;
import com.omni.procurement.dto.OverviewViews;

import java.util.List;

/**
 * 采购概览服务。
 *
 * @author Omni-Stack Team
 */
public interface OverviewService {

    /**
     * 查询采购概览摘要。
     *
     * @return 概览摘要
     */
    OverviewViews.Summary summary();

    /**
     * 按指定维度查询采购支出。
     *
     * @param query 查询条件
     * @return 支出分析项
     */
    List<OverviewViews.SpendItem> spendAnalysis(OverviewRequests.SpendAnalysisQuery query);
}
