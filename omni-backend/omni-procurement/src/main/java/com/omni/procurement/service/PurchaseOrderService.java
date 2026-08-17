package com.omni.procurement.service;

import com.omni.common.core.result.PageResult;
import com.omni.procurement.dto.PurchaseOrderContracts;
import com.omni.procurement.dto.PurchaseOrderRequests;
import com.omni.procurement.dto.PurchaseOrderViews;
import com.omni.procurement.entity.ProcRfq;
import com.omni.procurement.entity.ProcRfqLine;

import java.util.List;

/**
 * 采购订单服务。
 *
 * @author Omni-Stack Team
 */
public interface PurchaseOrderService {

    /**
     * 分页查询采购订单。
     *
     * @param query 查询条件
     * @return 采购订单分页
     */
    PageResult<PurchaseOrderViews.Summary> page(PurchaseOrderRequests.Query query);

    /**
     * 查询采购订单详情及累计收货进度。
     *
     * @param id 采购订单 ID
     * @return 采购订单详情
     */
    PurchaseOrderViews.Detail get(Long id);

    /**
     * 在 RFQ 定点事务内复制当前有效报价并生成采购订单。
     * <p>调用方必须已经锁定 RFQ；本方法加入调用方事务，不修改 RFQ 状态。</p>
     *
     * @param rfq 已锁定 RFQ
     * @param rfqLines RFQ 不可变行快照
     * @param quotation SRM 当前有效报价
     * @param terms 订单交付条款
     * @return 新建或同意图幂等重放的采购订单
     */
    PurchaseOrderViews.Detail createFromAward(
            ProcRfq rfq,
            List<ProcRfqLine> rfqLines,
            PurchaseOrderContracts.QuotationSnapshot quotation,
            PurchaseOrderRequests.AwardTerms terms);

    /**
     * 更新草稿订单交付信息。
     *
     * @param id 采购订单 ID
     * @param request 更新请求
     * @return 更新后详情
     */
    PurchaseOrderViews.Detail update(Long id, PurchaseOrderRequests.UpdateRequest request);

    /**
     * 删除草稿采购订单。
     *
     * @param id 采购订单 ID
     * @param version 乐观锁版本
     */
    void delete(Long id, Integer version);

    /**
     * 发送采购订单。
     *
     * @param id 采购订单 ID
     * @param version 乐观锁版本
     * @return 更新后详情
     */
    PurchaseOrderViews.Detail send(Long id, Integer version);

    /**
     * 确认采购订单并写入 Outbox。
     *
     * @param id 采购订单 ID
     * @param version 乐观锁版本
     * @return 更新后详情
     */
    PurchaseOrderViews.Detail confirm(Long id, Integer version);

    /**
     * 取消尚未收货的采购订单。
     *
     * @param id 采购订单 ID
     * @param version 乐观锁版本
     * @return 更新后详情
     */
    PurchaseOrderViews.Detail cancel(Long id, Integer version);
}
