package com.omni.procurement.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.omni.procurement.entity.ProcPurchaseOrder;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

/**
 * 采购订单 Mapper。
 *
 * @author Omni-Stack Team
 */
public interface ProcPurchaseOrderMapper extends BaseMapper<ProcPurchaseOrder> {

    /**
     * 按租户和主键锁定可见采购订单。
     *
     * @param tenantId 租户 ID
     * @param id 采购订单 ID
     * @return 已锁定采购订单
     */
    @Select("SELECT * FROM proc_purchase_order WHERE tenant_id = #{tenantId} "
            + "AND id = #{id} AND deleted = 0 FOR UPDATE")
    ProcPurchaseOrder selectForUpdate(@Param("tenantId") Long tenantId, @Param("id") Long id);

    /**
     * 按来源询价单锁定已生成的采购订单。
     *
     * @param tenantId 租户 ID
     * @param rfqId 询价单 ID
     * @return 已生成采购订单
     */
    @Select("SELECT * FROM proc_purchase_order WHERE tenant_id = #{tenantId} "
            + "AND rfq_id = #{rfqId} AND deleted = 0 FOR UPDATE")
    ProcPurchaseOrder selectForUpdateByRfq(@Param("tenantId") Long tenantId,
                                           @Param("rfqId") Long rfqId);
}
