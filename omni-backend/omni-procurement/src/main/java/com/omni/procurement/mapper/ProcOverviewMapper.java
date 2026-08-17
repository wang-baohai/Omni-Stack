package com.omni.procurement.mapper;

import com.omni.procurement.dto.OverviewViews;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

/**
 * 采购概览聚合 Mapper。
 * <p>所有查询直接命中对应聚合根，使 TenantLine 与 DataPermission
 * 能追加和业务列表一致的租户及 requester/owner 条件。</p>
 *
 * @author Omni-Stack Team
 */
public interface ProcOverviewMapper {

    /**
     * 统计审批中的请购数量。
     *
     * @return 请购数量
     */
    @Select("SELECT COUNT(*) FROM proc_requisition requisition "
            + "WHERE requisition.deleted = 0 AND requisition.status = 'APPROVING'")
    Long countPendingApprovalRequisitions();

    /**
     * 统计仍等待至少一个供应商报价的有效询价单数量。
     *
     * @return 询价单数量
     */
    @Select("SELECT COUNT(*) FROM proc_rfq rfq "
            + "WHERE rfq.deleted = 0 AND rfq.status = 'SENT' "
            + "AND rfq.quotation_deadline >= CURRENT_TIMESTAMP "
            + "AND EXISTS (SELECT 1 FROM proc_rfq_supplier invitation "
            + "WHERE invitation.rfq_id = rfq.id AND invitation.tenant_id = rfq.tenant_id "
            + "AND invitation.deleted = 0 AND invitation.status = 'INVITED')")
    Long countWaitingQuotationRfqs();

    /**
     * 按状态统计采购订单。
     *
     * @return 状态计数
     */
    @Select("SELECT purchase_order.status AS status, COUNT(*) AS count "
            + "FROM proc_purchase_order purchase_order "
            + "WHERE purchase_order.deleted = 0 GROUP BY purchase_order.status")
    List<OverviewViews.StatusCount> selectPurchaseOrderStatusCounts();

    /**
     * 统计收货草稿数量。
     *
     * @return 草稿数量
     */
    @Select("SELECT COUNT(*) FROM proc_goods_receipt goods_receipt "
            + "WHERE goods_receipt.deleted = 0 AND goods_receipt.status = 'DRAFT'")
    Long countDraftGoodsReceipts();

    /**
     * 按币种统计已确认采购承诺金额。
     *
     * @return 币种金额列表
     */
    @Select("SELECT purchase_order.currency_code AS currency_code, "
            + "SUM(purchase_order.total_amount) AS amount "
            + "FROM proc_purchase_order purchase_order "
            + "WHERE purchase_order.deleted = 0 AND purchase_order.status IN "
            + "('CONFIRMED','PARTIAL_RECEIVED','RECEIVED','CLOSED') "
            + "GROUP BY purchase_order.currency_code ORDER BY purchase_order.currency_code")
    List<OverviewViews.CurrencyAmount> selectCommittedAmountsByCurrency();

    /**
     * 按品类和币种统计采购支出。
     *
     * @param limit 最大返回条数
     * @return 支出分析项
     */
    @Select("SELECT purchase_line.category_code AS dimension_key, "
            + "COALESCE(category.category_name, purchase_line.category_code) AS dimension_name, "
            + "purchase_order.currency_code AS currency_code, SUM(purchase_line.total_price) AS amount "
            + "FROM proc_purchase_order purchase_order "
            + "JOIN proc_purchase_order_line purchase_line "
            + "ON purchase_line.po_id = purchase_order.id "
            + "AND purchase_line.tenant_id = purchase_order.tenant_id "
            + "LEFT JOIN proc_material_category category "
            + "ON category.category_code = purchase_line.category_code "
            + "AND category.tenant_id = purchase_order.tenant_id AND category.deleted = 0 "
            + "WHERE purchase_order.deleted = 0 AND purchase_line.deleted = 0 "
            + "AND purchase_order.status IN ('CONFIRMED','PARTIAL_RECEIVED','RECEIVED','CLOSED') "
            + "GROUP BY purchase_line.category_code, category.category_name, purchase_order.currency_code "
            + "ORDER BY purchase_order.currency_code, amount DESC, purchase_line.category_code "
            + "LIMIT #{limit}")
    List<OverviewViews.SpendItem> selectCategorySpend(@Param("limit") int limit);

    /**
     * 按供应商和币种统计采购支出。
     *
     * @param limit 最大返回条数
     * @return 支出分析项
     */
    @Select("SELECT CAST(purchase_order.supplier_id AS CHAR) AS dimension_key, "
            + "MAX(purchase_order.supplier_name_snapshot) AS dimension_name, "
            + "purchase_order.currency_code AS currency_code, "
            + "SUM(purchase_order.total_amount) AS amount "
            + "FROM proc_purchase_order purchase_order "
            + "WHERE purchase_order.deleted = 0 AND purchase_order.status IN "
            + "('CONFIRMED','PARTIAL_RECEIVED','RECEIVED','CLOSED') "
            + "GROUP BY purchase_order.supplier_id, purchase_order.currency_code "
            + "ORDER BY purchase_order.currency_code, amount DESC, purchase_order.supplier_id "
            + "LIMIT #{limit}")
    List<OverviewViews.SpendItem> selectSupplierSpend(@Param("limit") int limit);

    /**
     * 按采购订单负责部门和币种统计采购支出。
     *
     * @param limit 最大返回条数
     * @return 支出分析项
     */
    @Select("SELECT CAST(purchase_order.owner_unit_id AS CHAR) AS dimension_key, "
            + "CAST(purchase_order.owner_unit_id AS CHAR) AS dimension_name, "
            + "purchase_order.currency_code AS currency_code, "
            + "SUM(purchase_order.total_amount) AS amount "
            + "FROM proc_purchase_order purchase_order "
            + "WHERE purchase_order.deleted = 0 AND purchase_order.status IN "
            + "('CONFIRMED','PARTIAL_RECEIVED','RECEIVED','CLOSED') "
            + "GROUP BY purchase_order.owner_unit_id, purchase_order.currency_code "
            + "ORDER BY purchase_order.currency_code, amount DESC, purchase_order.owner_unit_id "
            + "LIMIT #{limit}")
    List<OverviewViews.SpendItem> selectDepartmentSpend(@Param("limit") int limit);
}
