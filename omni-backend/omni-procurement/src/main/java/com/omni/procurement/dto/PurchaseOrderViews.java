package com.omni.procurement.dto;

import lombok.Data;
import lombok.EqualsAndHashCode;

import java.io.Serial;
import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 采购订单响应 DTO 集合。
 *
 * @author Omni-Stack Team
 */
public final class PurchaseOrderViews {

    private PurchaseOrderViews() {
    }

    /** 采购订单列表摘要。 */
    @Data
    public static class Summary implements Serializable {
        @Serial private static final long serialVersionUID = 1L;
        /** 主键。 */ private Long id;
        /** 采购订单号。 */ private String poNo;
        /** 来源 RFQ ID。 */ private Long rfqId;
        /** 供应商 ID。 */ private Long supplierId;
        /** 供应商名称快照。 */ private String supplierNameSnapshot;
        /** 订单标题。 */ private String title;
        /** 订单总金额。 */
        @com.fasterxml.jackson.databind.annotation.JsonSerialize(
                using = com.fasterxml.jackson.databind.ser.std.ToStringSerializer.class)
        @tools.jackson.databind.annotation.JsonSerialize(
                using = tools.jackson.databind.ser.std.ToStringSerializer.class)
        private BigDecimal totalAmount;
        /** 币种。 */ private String currencyCode;
        /** 订单状态。 */ private String status;
        /** 订单发送时间。 */ private LocalDateTime orderTime;
        /** 预计交付日期。 */ private LocalDate expectedDeliveryDate;
        /** 实际全部收货日期。 */ private LocalDate actualDeliveryDate;
        /** 掩码收货地址。 */ private String deliveryAddressMasked;
        /** 掩码联系人。 */ private String contactNameMasked;
        /** 掩码联系电话。 */ private String contactPhoneMasked;
        /** 负责人用户 ID。 */ private Long ownerUserId;
        /** 负责人组织 ID。 */ private Long ownerUnitId;
        /** 乐观锁版本。 */ private Integer version;
        /** 创建时间。 */ private LocalDateTime createTime;
        /** 更新时间。 */ private LocalDateTime updateTime;
    }

    /** 采购订单详情。 */
    @Data
    @EqualsAndHashCode(callSuper = true)
    public static class Detail extends Summary {
        @Serial private static final long serialVersionUID = 1L;
        /** 报价 ID。 */ private Long quotationId;
        /** 报价版本。 */ private Integer quotationVersion;
        /** 完整收货地址。 */ private String deliveryAddress;
        /** 完整联系人。 */ private String contactName;
        /** 完整联系电话。 */ private String contactPhone;
        /** 订单行。 */ private List<Line> lines;
    }

    /** 采购订单行及累计收货进度。 */
    @Data
    public static class Line implements Serializable {
        @Serial private static final long serialVersionUID = 1L;
        /** 主键。 */ private Long id;
        /** 行号。 */ private Integer lineNo;
        /** 来源 RFQ 行 ID。 */ private Long rfqLineId;
        /** 物料 ID。 */ private Long materialId;
        /** 物料编码快照。 */ private String materialCode;
        /** 物料名称快照。 */ private String materialName;
        /** 品类编码快照。 */ private String categoryCode;
        /** 计量单位快照。 */ private String unit;
        /** 订单数量。 */
        @com.fasterxml.jackson.databind.annotation.JsonSerialize(
                using = com.fasterxml.jackson.databind.ser.std.ToStringSerializer.class)
        @tools.jackson.databind.annotation.JsonSerialize(
                using = tools.jackson.databind.ser.std.ToStringSerializer.class)
        private BigDecimal quantity;
        /** 中标单价。 */
        @com.fasterxml.jackson.databind.annotation.JsonSerialize(
                using = com.fasterxml.jackson.databind.ser.std.ToStringSerializer.class)
        @tools.jackson.databind.annotation.JsonSerialize(
                using = tools.jackson.databind.ser.std.ToStringSerializer.class)
        private BigDecimal unitPrice;
        /** 行金额。 */
        @com.fasterxml.jackson.databind.annotation.JsonSerialize(
                using = com.fasterxml.jackson.databind.ser.std.ToStringSerializer.class)
        @tools.jackson.databind.annotation.JsonSerialize(
                using = tools.jackson.databind.ser.std.ToStringSerializer.class)
        private BigDecimal totalPrice;
        /** 报价交付天数。 */ private Integer deliveryDays;
        /** 行预计交付日期。 */ private LocalDate expectedDeliveryDate;
        /** 已确认累计收货数量。 */
        @com.fasterxml.jackson.databind.annotation.JsonSerialize(
                using = com.fasterxml.jackson.databind.ser.std.ToStringSerializer.class)
        @tools.jackson.databind.annotation.JsonSerialize(
                using = tools.jackson.databind.ser.std.ToStringSerializer.class)
        private BigDecimal receivedQuantity;
        /** 剩余可收数量。 */
        @com.fasterxml.jackson.databind.annotation.JsonSerialize(
                using = com.fasterxml.jackson.databind.ser.std.ToStringSerializer.class)
        @tools.jackson.databind.annotation.JsonSerialize(
                using = tools.jackson.databind.ser.std.ToStringSerializer.class)
        private BigDecimal remainingQuantity;
        /** 行备注快照。 */ private String remark;
        /** 乐观锁版本。 */ private Integer version;
    }
}
