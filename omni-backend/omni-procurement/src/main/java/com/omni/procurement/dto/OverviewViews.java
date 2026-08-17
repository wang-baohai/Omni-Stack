package com.omni.procurement.dto;

import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.math.BigDecimal;
import java.util.List;

/**
 * 采购概览响应 DTO 集合。
 *
 * @author Omni-Stack Team
 */
public final class OverviewViews {

    private OverviewViews() {
    }

    /** 采购概览摘要。 */
    @Data
    public static class Summary implements Serializable {

        @Serial
        private static final long serialVersionUID = 1L;

        /** 审批中的请购数量。 */
        private Long pendingApprovalRequisitionCount;

        /** 截止时间内仍有供应商待报价的询价单数量。 */
        private Long waitingQuotationRfqCount;

        /** 采购订单各状态数量，包含零值状态。 */
        private List<StatusCount> purchaseOrderStatusCounts;

        /** 尚未确认的收货草稿数量。 */
        private Long draftGoodsReceiptCount;

        /** 已确认采购承诺金额，严格按币种分组。 */
        private List<CurrencyAmount> committedAmountsByCurrency;
    }

    /** 状态计数。 */
    @Data
    public static class StatusCount implements Serializable {

        @Serial
        private static final long serialVersionUID = 1L;

        /** 状态编码。 */
        private String status;

        /** 记录数量。 */
        private Long count;
    }

    /** 币种金额汇总。 */
    @Data
    public static class CurrencyAmount implements Serializable {

        @Serial
        private static final long serialVersionUID = 1L;

        /** ISO 4217 币种编码。 */
        private String currencyCode;

        /** 该币种金额。 */
        @com.fasterxml.jackson.databind.annotation.JsonSerialize(
                using = com.fasterxml.jackson.databind.ser.std.ToStringSerializer.class)
        @tools.jackson.databind.annotation.JsonSerialize(
                using = tools.jackson.databind.ser.std.ToStringSerializer.class)
        private BigDecimal amount;
    }

    /** 支出分析项。 */
    @Data
    public static class SpendItem implements Serializable {

        @Serial
        private static final long serialVersionUID = 1L;

        /** CATEGORY/SUPPLIER/DEPARTMENT。 */
        private OverviewRequests.SpendDimension dimension;

        /** 品类编码、供应商 ID 或负责部门 ID。 */
        private String dimensionKey;

        /** 品类名称、供应商名称快照；部门暂返回 ID 文本。 */
        private String dimensionName;

        /** ISO 4217 币种编码。 */
        private String currencyCode;

        /** 已确认采购承诺金额。 */
        @com.fasterxml.jackson.databind.annotation.JsonSerialize(
                using = com.fasterxml.jackson.databind.ser.std.ToStringSerializer.class)
        @tools.jackson.databind.annotation.JsonSerialize(
                using = tools.jackson.databind.ser.std.ToStringSerializer.class)
        private BigDecimal amount;
    }
}
