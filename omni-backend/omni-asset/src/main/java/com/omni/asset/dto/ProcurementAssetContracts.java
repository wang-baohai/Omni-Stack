package com.omni.asset.dto;

import lombok.Builder;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/**
 * Procurement 收货资产化的跨服务契约。
 *
 * @author Omni-Stack Team
 */
public final class ProcurementAssetContracts {

    private ProcurementAssetContracts() {
    }

    /** Procurement 收货领域事件信封。 */
    @Data
    public static class GoodsReceiptEvent implements Serializable {
        @Serial private static final long serialVersionUID = 1L;
        /** 全局事件 ID。 */ private String eventId;
        /** 带版本的事件类型。 */ private String eventType;
        /** 事件发生时间。 */ private LocalDateTime occurredAt;
        /** 租户 ID。 */ private Long tenantId;
        /** 收货业务载荷。 */ private GoodsReceiptPayload payload;
    }

    /** 收货事件业务载荷。 */
    @Data
    public static class GoodsReceiptPayload implements Serializable {
        @Serial private static final long serialVersionUID = 1L;
        /** 收货单 ID。 */ private Long goodsReceiptId;
        /** 收货单号。 */ private String grNo;
        /** 采购订单 ID。 */ private Long purchaseOrderId;
        /** 采购订单号。 */ private String poNo;
        /** 供应商 ID。 */ private Long supplierId;
        /** 供应商名称快照。 */ private String supplierNameSnapshot;
        /** 采购收货时间。 */ private LocalDateTime purchaseDate;
        /** 币种。 */ private String currencyCode;
        /** 资产管理员用户 ID。 */ private Long ownerUserId;
        /** 资产管理部门 ID。 */ private Long ownerUnitId;
        /** 收货行。 */ private List<GoodsReceiptLine> lines;
    }

    /** 收货事件资产候选行。 */
    @Data
    public static class GoodsReceiptLine implements Serializable {
        @Serial private static final long serialVersionUID = 1L;
        /** 收货行 ID。 */ private Long goodsReceiptLineId;
        /** 采购订单行 ID。 */ private Long purchaseOrderLineId;
        /** 物料 ID。 */ private Long materialId;
        /** 物料编码快照。 */ private String materialCode;
        /** 物料名称快照。 */ private String materialNameSnapshot;
        /** 品类编码快照。 */ private String categoryCode;
        /** 计量单位快照。 */ private String unit;
        /** 本次收货数量，只接受 JSON 十进制字符串。 */
        @com.fasterxml.jackson.databind.annotation.JsonDeserialize(
                using = Jackson2DecimalStringDeserializer.class)
        @tools.jackson.databind.annotation.JsonDeserialize(
                using = Jackson3DecimalStringDeserializer.class)
        private BigDecimal receivedQuantity;
        /** 质检状态。 */ private String qualityStatus;
        /** 是否进入资产管理。 */ private Boolean assetManaged;
        /** 应创建的单位资产数量。 */ private Long assetQuantity;
        /** 单位采购金额，只接受 JSON 十进制字符串。 */
        @com.fasterxml.jackson.databind.annotation.JsonDeserialize(
                using = Jackson2DecimalStringDeserializer.class)
        @tools.jackson.databind.annotation.JsonDeserialize(
                using = Jackson3DecimalStringDeserializer.class)
        private BigDecimal unitPrice;
        /** 本行采购金额，只接受 JSON 十进制字符串。 */
        @com.fasterxml.jackson.databind.annotation.JsonDeserialize(
                using = Jackson2DecimalStringDeserializer.class)
        @tools.jackson.databind.annotation.JsonDeserialize(
                using = Jackson3DecimalStringDeserializer.class)
        private BigDecimal totalPrice;
    }

    /** Procurement 历史资产候选行。 */
    @Data
    public static class AssetCandidate implements Serializable {
        @Serial private static final long serialVersionUID = 1L;
        /** 原资产化事件 ID。 */ private String eventId;
        /** 收货单 ID。 */ private Long goodsReceiptId;
        /** 收货单号。 */ private String grNo;
        /** 采购订单 ID。 */ private Long purchaseOrderId;
        /** 采购订单号。 */ private String poNo;
        /** 供应商 ID。 */ private Long supplierId;
        /** 供应商名称快照。 */ private String supplierNameSnapshot;
        /** 采购收货时间。 */ private LocalDateTime purchaseDate;
        /** 币种。 */ private String currencyCode;
        /** 资产管理员用户 ID。 */ private Long ownerUserId;
        /** 资产管理部门 ID。 */ private Long ownerUnitId;
        /** 收货行 ID。 */ private Long goodsReceiptLineId;
        /** 采购订单行 ID。 */ private Long purchaseOrderLineId;
        /** 物料 ID。 */ private Long materialId;
        /** 物料编码快照。 */ private String materialCode;
        /** 物料名称快照。 */ private String materialNameSnapshot;
        /** 品类编码快照。 */ private String categoryCode;
        /** 计量单位快照。 */ private String unit;
        /** 本次收货数量，只接受 JSON 十进制字符串。 */
        @com.fasterxml.jackson.databind.annotation.JsonDeserialize(
                using = Jackson2DecimalStringDeserializer.class)
        @tools.jackson.databind.annotation.JsonDeserialize(
                using = Jackson3DecimalStringDeserializer.class)
        private BigDecimal receivedQuantity;
        /** 质检状态。 */ private String qualityStatus;
        /** 是否进入资产管理。 */ private Boolean assetManaged;
        /** 应创建的单位资产数量。 */ private Long assetQuantity;
        /** 单位采购金额，只接受 JSON 十进制字符串。 */
        @com.fasterxml.jackson.databind.annotation.JsonDeserialize(
                using = Jackson2DecimalStringDeserializer.class)
        @tools.jackson.databind.annotation.JsonDeserialize(
                using = Jackson3DecimalStringDeserializer.class)
        private BigDecimal unitPrice;
        /** 本行采购金额，只接受 JSON 十进制字符串。 */
        @com.fasterxml.jackson.databind.annotation.JsonDeserialize(
                using = Jackson2DecimalStringDeserializer.class)
        @tools.jackson.databind.annotation.JsonDeserialize(
                using = Jackson3DecimalStringDeserializer.class)
        private BigDecimal totalPrice;
    }

    /** 单次导入结果。 */
    @Data
    @Builder
    public static class ImportResult implements Serializable {
        @Serial private static final long serialVersionUID = 1L;
        /** 本次新建资产 ID。 */ private List<Long> createdAssetIds;
        /** 本次新建数量。 */ private int createdCount;
        /** 来源单位已存在数量。 */ private int duplicateCount;
        /** 不满足资产化条件的行数。 */ private int ignoredLineCount;
        /** 是否为已处理事件重放。 */ private boolean replayed;
    }

    /** 一页历史补偿结果。 */
    @Data
    @Builder
    public static class BackfillResult implements Serializable {
        @Serial private static final long serialVersionUID = 1L;
        /** 请求起始游标。 */ private Long afterId;
        /** 下一页起始游标。 */ private Long nextAfterId;
        /** Procurement 返回候选行数。 */ private int fetchedCount;
        /** 本页新建资产数量。 */ private int createdCount;
        /** 本页已存在资产数量。 */ private int duplicateCount;
        /** 是否可能仍有下一页。 */ private boolean hasMore;
    }
}
