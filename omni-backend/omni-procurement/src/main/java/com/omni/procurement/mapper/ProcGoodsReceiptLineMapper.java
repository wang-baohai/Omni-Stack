package com.omni.procurement.mapper;

import com.baomidou.mybatisplus.annotation.InterceptorIgnore;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.omni.procurement.dto.GoodsReceiptContracts;
import com.omni.procurement.entity.ProcGoodsReceiptLine;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

/**
 * 收货单行 Mapper。
 *
 * @author Omni-Stack Team
 */
public interface ProcGoodsReceiptLineMapper extends BaseMapper<ProcGoodsReceiptLine> {

    /**
     * 锁定指定收货单的所有有效行。
     *
     * @param tenantId 租户 ID
     * @param goodsReceiptId 收货单 ID
     * @return 已锁定收货行
     */
    @Select("SELECT * FROM proc_goods_receipt_line WHERE tenant_id = #{tenantId} "
            + "AND goods_receipt_id = #{goodsReceiptId} AND deleted = 0 "
            + "ORDER BY line_no FOR UPDATE")
    List<ProcGoodsReceiptLine> selectForUpdateByReceipt(
            @Param("tenantId") Long tenantId,
            @Param("goodsReceiptId") Long goodsReceiptId);

    /**
     * 汇总订单全部已确认收货数量。
     * <p>调用方必须先验证并锁定当前租户可见的订单；此查询忽略收货人数据范围，
     * 避免遗漏其他收货人已确认数量而造成超收。</p>
     *
     * @param tenantId 租户 ID
     * @param poId 采购订单 ID
     * @return 各订单行累计收货数量
     */
    @InterceptorIgnore(dataPermission = "true")
    @Select("SELECT line.po_line_id AS po_line_id, "
            + "SUM(line.received_quantity) AS total_quantity "
            + "FROM proc_goods_receipt_line line "
            + "INNER JOIN proc_goods_receipt receipt "
            + "ON receipt.tenant_id = line.tenant_id "
            + "AND receipt.id = line.goods_receipt_id "
            + "AND receipt.deleted = 0 "
            + "WHERE line.tenant_id = #{tenantId} AND receipt.po_id = #{poId} "
            + "AND receipt.status = 'CONFIRMED' AND line.deleted = 0 "
            + "GROUP BY line.po_line_id")
    List<GoodsReceiptContracts.ReceivedTotal> selectConfirmedTotals(
            @Param("tenantId") Long tenantId, @Param("poId") Long poId);

    /**
     * 游标查询历史资产候选收货行。
     *
     * @param tenantId 租户 ID
     * @param afterId 起始行 ID（不含）
     * @param size 返回上限
     * @return 可资产化的已确认收货行
     */
    @InterceptorIgnore(dataPermission = "true")
    @Select("SELECT line.* FROM proc_goods_receipt_line line "
            + "INNER JOIN proc_goods_receipt receipt "
            + "ON receipt.tenant_id = line.tenant_id "
            + "AND receipt.id = line.goods_receipt_id "
            + "AND receipt.deleted = 0 "
            + "WHERE line.tenant_id = #{tenantId} AND line.id > #{afterId} "
            + "AND line.deleted = 0 AND receipt.status = 'CONFIRMED' "
            + "AND line.asset_managed = 1 AND line.quality_status = 'PASS' "
            + "AND line.received_quantity > 0 "
            + "AND line.received_quantity = FLOOR(line.received_quantity) "
            + "AND (line.quality_passed_event_id IS NOT NULL "
            + "OR line.confirmed_event_id IS NOT NULL) "
            + "ORDER BY line.id LIMIT #{size}")
    List<ProcGoodsReceiptLine> selectAssetCandidateLines(
            @Param("tenantId") Long tenantId,
            @Param("afterId") Long afterId,
            @Param("size") Integer size);
}
