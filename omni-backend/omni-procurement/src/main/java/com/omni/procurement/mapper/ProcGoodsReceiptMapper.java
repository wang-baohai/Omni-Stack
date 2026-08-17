package com.omni.procurement.mapper;

import com.baomidou.mybatisplus.annotation.InterceptorIgnore;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.omni.procurement.entity.ProcGoodsReceipt;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.time.LocalDateTime;

/**
 * 收货单 Mapper。
 *
 * @author Omni-Stack Team
 */
public interface ProcGoodsReceiptMapper extends BaseMapper<ProcGoodsReceipt> {

    /**
     * 按租户和主键锁定可见收货单。
     *
     * @param tenantId 租户 ID
     * @param id 收货单 ID
     * @return 已锁定收货单
     */
    @Select("SELECT * FROM proc_goods_receipt WHERE tenant_id = #{tenantId} "
            + "AND id = #{id} AND deleted = 0 FOR UPDATE")
    ProcGoodsReceipt selectForUpdate(@Param("tenantId") Long tenantId, @Param("id") Long id);

    /**
     * 查询采购订单全部已确认收货单中的最晚业务收货时间。
     * <p>调用方必须已经验证并锁定当前租户可见的采购订单。</p>
     *
     * @param tenantId 租户 ID
     * @param poId 采购订单 ID
     * @return 最晚业务收货时间
     */
    @InterceptorIgnore(dataPermission = "true")
    @Select("SELECT MAX(receive_time) FROM proc_goods_receipt "
            + "WHERE tenant_id = #{tenantId} AND po_id = #{poId} "
            + "AND status = 'CONFIRMED' AND deleted = 0")
    LocalDateTime selectMaxConfirmedReceiveTime(
            @Param("tenantId") Long tenantId, @Param("poId") Long poId);
}
