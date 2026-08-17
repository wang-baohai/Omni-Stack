package com.omni.asset.service;

import com.omni.asset.dto.ProcurementAssetContracts;

/**
 * Procurement 收货资产化导入服务。
 *
 * @author Omni-Stack Team
 */
public interface ProcurementAssetImportService {

    /**
     * 幂等处理实时收货领域事件。
     *
     * @param event 收货领域事件
     * @return 导入结果
     */
    ProcurementAssetContracts.ImportResult importEvent(
            ProcurementAssetContracts.GoodsReceiptEvent event);

    /**
     * 幂等处理一条历史补偿候选。
     *
     * @param tenantId 租户 ID
     * @param candidate 历史候选行
     * @return 导入结果
     */
    ProcurementAssetContracts.ImportResult importCandidate(
            Long tenantId, ProcurementAssetContracts.AssetCandidate candidate);
}
