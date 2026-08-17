package com.omni.asset.service;

import com.omni.asset.dto.ProcurementAssetContracts;

/**
 * Procurement 历史资产候选补偿服务。
 *
 * @author Omni-Stack Team
 */
public interface ProcurementAssetBackfillService {

    /**
     * 回扫并导入一页历史资产候选。
     *
     * @param tenantId 租户 ID
     * @param afterId 起始收货行 ID（不含）
     * @param size 页大小
     * @return 本页补偿结果
     */
    ProcurementAssetContracts.BackfillResult backfill(
            Long tenantId, Long afterId, Integer size);
}
