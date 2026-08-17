package com.omni.procurement.service;

import com.omni.procurement.dto.GoodsReceiptViews;

import java.util.List;

/**
 * Asset 历史补偿回扫内部服务。
 *
 * @author Omni-Stack Team
 */
public interface InternalAssetCandidateService {

    /**
     * 使用收货行 ID 游标查询可资产化历史记录。
     *
     * @param tenantId 租户 ID
     * @param afterId 起始行 ID（不含）
     * @param size 返回上限
     * @return 资产候选行
     */
    List<GoodsReceiptViews.AssetCandidate> list(Long tenantId, Long afterId, Integer size);
}
