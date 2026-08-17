package com.omni.procurement.service;

import com.omni.common.core.result.PageResult;
import com.omni.procurement.dto.GoodsReceiptRequests;
import com.omni.procurement.dto.GoodsReceiptViews;

/**
 * 收货单服务。
 *
 * @author Omni-Stack Team
 */
public interface GoodsReceiptService {

    /**
     * 分页查询收货单。
     *
     * @param query 查询条件
     * @return 收货单分页
     */
    PageResult<GoodsReceiptViews.Summary> page(GoodsReceiptRequests.Query query);

    /**
     * 查询收货单详情。
     *
     * @param id 收货单 ID
     * @return 收货单详情
     */
    GoodsReceiptViews.Detail get(Long id);

    /**
     * 创建不占用订单可收数量的收货草稿。
     *
     * @param request 创建请求
     * @return 收货单详情
     */
    GoodsReceiptViews.Detail create(GoodsReceiptRequests.CreateRequest request);

    /**
     * 确认收货并累计校验订单数量。
     *
     * @param id 收货单 ID
     * @param version 乐观锁版本
     * @return 收货单详情
     */
    GoodsReceiptViews.Detail confirm(Long id, Integer version);

    /**
     * 登记已确认收货单的后续质检结果。
     *
     * @param id 收货单 ID
     * @param command 质检结果命令
     * @return 收货单详情
     */
    GoodsReceiptViews.Detail updateQualityResult(
            Long id, GoodsReceiptRequests.QualityResultCommand command);
}
