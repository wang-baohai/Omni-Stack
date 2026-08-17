package com.omni.procurement.service;

import com.omni.common.core.result.PageResult;
import com.omni.procurement.dto.PurchaseOrderContracts;
import com.omni.procurement.dto.RfqRequests;
import com.omni.procurement.dto.RfqViews;

import java.util.List;

/**
 * 询价单服务。
 *
 * @author Omni-Stack Team
 */
public interface RfqService {

    /**
     * 查询创建询价可选择的当前合格供应商。
     *
     * @param query 查询条件
     * @return 无 PII 供应商选项
     */
    List<RfqViews.SupplierOption> supplierOptions(RfqRequests.SupplierOptionQuery query);

    /**
     * 分页查询询价单。
     *
     * @param query 查询条件
     * @return 询价分页
     */
    PageResult<RfqViews.Summary> page(RfqRequests.Query query);

    /**
     * 查询询价详情。
     *
     * @param id 询价单 ID
     * @return 询价详情
     */
    RfqViews.Detail get(Long id);

    /**
     * 查询当前有效报价并生成比价快照。
     *
     * @param id 询价单 ID
     * @return 当前有效报价
     */
    List<PurchaseOrderContracts.QuotationSnapshot> comparison(Long id);

    /**
     * 从已审批请购申请创建询价草稿。
     *
     * @param request 创建请求
     * @return 询价详情
     */
    RfqViews.Detail create(RfqRequests.CreateRequest request);

    /**
     * 更新询价草稿及供应商邀请。
     *
     * @param id 询价单 ID
     * @param request 更新请求
     * @return 询价详情
     */
    RfqViews.Detail update(Long id, RfqRequests.UpdateRequest request);

    /**
     * 删除询价草稿。
     *
     * @param id 询价单 ID
     * @param version 乐观锁版本
     */
    void delete(Long id, Integer version);

    /**
     * 发送询价并在同一事务写入 Outbox。
     *
     * @param id 询价单 ID
     * @param version 乐观锁版本
     * @return 已发送询价详情
     */
    RfqViews.Detail send(Long id, Integer version);

    /**
     * 按指定报价版本定点并生成采购订单。
     *
     * @param id 询价单 ID
     * @param request 定点命令
     * @return RFQ 与采购订单定点结果
     */
    RfqViews.AwardResult award(Long id, RfqRequests.AwardRequest request);

    /**
     * 取消草稿或已发送询价。
     *
     * @param id 询价单 ID
     * @param version 乐观锁版本
     * @return 已取消询价详情
     */
    RfqViews.Detail cancel(Long id, Integer version);
}
