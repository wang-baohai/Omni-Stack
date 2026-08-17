package com.omni.procurement.dto;

import lombok.Data;
import lombok.EqualsAndHashCode;

import java.io.Serial;
import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 收货单响应 DTO 集合。
 *
 * @author Omni-Stack Team
 */
public final class GoodsReceiptViews {

    private GoodsReceiptViews() {
    }

    /** 收货单列表摘要。 */
    @Data
    public static class Summary implements Serializable {
        @Serial private static final long serialVersionUID = 1L;
        /** 主键。 */ private Long id;
        /** 收货单号。 */ private String grNo;
        /** 采购订单 ID。 */ private Long poId;
        /** 采购订单号。 */ private String poNo;
        /** 收货人用户 ID。 */ private Long receiverUserId;
        /** 业务收货时间。 */ private LocalDateTime receiveTime;
        /** 收货备注。 */ private String remark;
        /** 收货单状态。 */ private String status;
        /** 确认时间。 */ private LocalDateTime confirmedTime;
        /** 负责人用户 ID。 */ private Long ownerUserId;
        /** 负责人组织 ID。 */ private Long ownerUnitId;
        /** 乐观锁版本。 */ private Integer version;
        /** 创建时间。 */ private LocalDateTime createTime;
        /** 更新时间。 */ private LocalDateTime updateTime;
    }

    /** 收货单详情。 */
    @Data
    @EqualsAndHashCode(callSuper = true)
    public static class Detail extends Summary {
        @Serial private static final long serialVersionUID = 1L;
        /** 收货行。 */ private List<Line> lines;
    }

    /** 收货单行。 */
    @Data
    public static class Line implements Serializable {
        @Serial private static final long serialVersionUID = 1L;
        /** 主键。 */ private Long id;
        /** 行号。 */ private Integer lineNo;
        /** 采购订单行 ID。 */ private Long poLineId;
        /** 物料 ID。 */ private Long materialId;
        /** 物料编码快照。 */ private String materialCode;
        /** 物料名称快照。 */ private String materialName;
        /** 品类编码快照。 */ private String categoryCode;
        /** 计量单位快照。 */ private String unit;
        /** 是否按单位创建资产。 */ private Boolean assetManaged;
        /** 订单数量快照。 */
        @com.fasterxml.jackson.databind.annotation.JsonSerialize(
                using = com.fasterxml.jackson.databind.ser.std.ToStringSerializer.class)
        @tools.jackson.databind.annotation.JsonSerialize(
                using = tools.jackson.databind.ser.std.ToStringSerializer.class)
        private BigDecimal orderedQuantity;
        /** 本次收货数量。 */
        @com.fasterxml.jackson.databind.annotation.JsonSerialize(
                using = com.fasterxml.jackson.databind.ser.std.ToStringSerializer.class)
        @tools.jackson.databind.annotation.JsonSerialize(
                using = tools.jackson.databind.ser.std.ToStringSerializer.class)
        private BigDecimal receivedQuantity;
        /** 质检状态。 */ private String qualityStatus;
        /** 质检结果时间。 */ private LocalDateTime qualityResultTime;
        /** 行备注。 */ private String remark;
        /** 乐观锁版本。 */ private Integer version;
    }

    /** Asset 实时消费和历史补偿统一候选行。 */
    @Data
    public static class AssetCandidate implements Serializable {
        @Serial private static final long serialVersionUID = 1L;
        /** 资产化来源事件 ID。 */ private String eventId;
        /** 收货单 ID。 */ private Long goodsReceiptId;
        /** 收货单号。 */ private String grNo;
        /** 采购订单 ID。 */ private Long purchaseOrderId;
        /** 采购订单号。 */ private String poNo;
        /** 供应商 ID。 */ private Long supplierId;
        /** 供应商名称快照。 */ private String supplierNameSnapshot;
        /** 采购收货日期。 */ private LocalDateTime purchaseDate;
        /** 币种。 */ private String currencyCode;
        /** 资产管理责任人，继承收货单负责人。 */ private Long ownerUserId;
        /** 资产管理责任部门，继承收货单负责部门。 */ private Long ownerUnitId;
        /** 收货行 ID。 */ private Long goodsReceiptLineId;
        /** 采购订单行 ID。 */ private Long purchaseOrderLineId;
        /** 物料 ID。 */ private Long materialId;
        /** 物料编码快照。 */ private String materialCode;
        /** 物料名称快照。 */ private String materialNameSnapshot;
        /** 品类编码快照。 */ private String categoryCode;
        /** 计量单位快照。 */ private String unit;
        /** 本次收货数量。 */
        @com.fasterxml.jackson.databind.annotation.JsonSerialize(
                using = com.fasterxml.jackson.databind.ser.std.ToStringSerializer.class)
        @tools.jackson.databind.annotation.JsonSerialize(
                using = tools.jackson.databind.ser.std.ToStringSerializer.class)
        private BigDecimal receivedQuantity;
        /** 质检状态。 */ private String qualityStatus;
        /** 是否资产管理。 */ private Boolean assetManaged;
        /** 需创建的资产卡片数量。 */ private Long assetQuantity;
        /** 中标单价。 */
        @com.fasterxml.jackson.databind.annotation.JsonSerialize(
                using = com.fasterxml.jackson.databind.ser.std.ToStringSerializer.class)
        @tools.jackson.databind.annotation.JsonSerialize(
                using = tools.jackson.databind.ser.std.ToStringSerializer.class)
        private BigDecimal unitPrice;
        /** 本次可资产化金额。 */
        @com.fasterxml.jackson.databind.annotation.JsonSerialize(
                using = com.fasterxml.jackson.databind.ser.std.ToStringSerializer.class)
        @tools.jackson.databind.annotation.JsonSerialize(
                using = tools.jackson.databind.ser.std.ToStringSerializer.class)
        private BigDecimal totalPrice;
    }
}
