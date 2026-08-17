package com.omni.asset.service;

import com.omni.asset.dto.AssetOverviewRequests;
import com.omni.asset.dto.AssetOverviewViews;

import java.util.List;

/**
 * 资产概览服务。
 *
 * @author Omni-Stack Team
 */
public interface AssetOverviewService {

    /**
     * 查询资产摘要。
     *
     * @return 摘要
     */
    AssetOverviewViews.Summary summary();

    /**
     * 查询资产分布。
     *
     * @param query 查询参数
     * @return 分布行
     */
    List<AssetOverviewViews.DistributionItem> distribution(
            AssetOverviewRequests.DistributionQuery query);
}
