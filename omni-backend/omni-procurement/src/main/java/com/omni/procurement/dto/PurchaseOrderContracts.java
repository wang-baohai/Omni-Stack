package com.omni.procurement.dto;

import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 采购订单定点所需的 SRM 报价只读契约。
 *
 * @author Omni-Stack Team
 */
public final class PurchaseOrderContracts {

    private PurchaseOrderContracts() {
    }

    /** SRM 当前有效报价快照。 */
    @Data
    public static class QuotationSnapshot implements Serializable {
        @Serial private static final long serialVersionUID = 1L;
        /** 报价 ID。 */ private Long id;
        /** RFQ ID。 */ private Long rfqId;
        /** RFQ 编号快照。 */ private String rfqNo;
        /** 供应商 ID。 */ private Long supplierId;
        /** 供应商名称快照。 */ private String supplierNameSnapshot;
        /** 报价时间。 */ private LocalDateTime quotationTime;
        /** 报价有效期。 */ private LocalDateTime validUntil;
        /** 报价总金额。 */
        @com.fasterxml.jackson.databind.annotation.JsonSerialize(
                using = com.fasterxml.jackson.databind.ser.std.ToStringSerializer.class)
        @tools.jackson.databind.annotation.JsonSerialize(
                using = tools.jackson.databind.ser.std.ToStringSerializer.class)
        private BigDecimal totalAmount;
        /** 币种。 */ private String currencyCode;
        /** 报价状态。 */ private String status;
        /** 报价版本。 */ private Integer version;
        /** 报价行。 */ private List<QuotationLineSnapshot> lines;
    }

    /** SRM 当前有效报价行快照。 */
    @Data
    public static class QuotationLineSnapshot implements Serializable {
        @Serial private static final long serialVersionUID = 1L;
        /** 报价行 ID。 */ private Long id;
        /** RFQ 行 ID。 */ private Long rfqLineId;
        /** 物料编码快照。 */ private String materialCode;
        /** 物料名称快照。 */ private String materialName;
        /** 计量单位快照。 */ private String unit;
        /** 中标单价。 */
        @com.fasterxml.jackson.databind.annotation.JsonSerialize(
                using = com.fasterxml.jackson.databind.ser.std.ToStringSerializer.class)
        @tools.jackson.databind.annotation.JsonSerialize(
                using = tools.jackson.databind.ser.std.ToStringSerializer.class)
        private BigDecimal unitPrice;
        /** 中标数量。 */
        @com.fasterxml.jackson.databind.annotation.JsonSerialize(
                using = com.fasterxml.jackson.databind.ser.std.ToStringSerializer.class)
        @tools.jackson.databind.annotation.JsonSerialize(
                using = tools.jackson.databind.ser.std.ToStringSerializer.class)
        private BigDecimal quantity;
        /** 行金额。 */
        @com.fasterxml.jackson.databind.annotation.JsonSerialize(
                using = com.fasterxml.jackson.databind.ser.std.ToStringSerializer.class)
        @tools.jackson.databind.annotation.JsonSerialize(
                using = tools.jackson.databind.ser.std.ToStringSerializer.class)
        private BigDecimal lineAmount;
        /** 交付天数。 */ private Integer deliveryDays;
        /** 供应商备注。 */ private String remark;
    }
}
