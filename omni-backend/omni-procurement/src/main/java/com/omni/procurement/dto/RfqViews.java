package com.omni.procurement.dto;

import lombok.Data;
import lombok.EqualsAndHashCode;

import java.io.Serial;
import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 询价响应 DTO 集合。
 *
 * @author Omni-Stack Team
 */
public final class RfqViews {

    private RfqViews() {
    }

    /** 询价列表摘要。 */
    @Data
    public static class Summary implements Serializable {
        @Serial private static final long serialVersionUID = 1L;
        /** 主键。 */ private Long id;
        /** 询价单号。 */ private String rfqNo;
        /** 来源请购申请 ID。 */ private Long requisitionId;
        /** 标题。 */ private String title;
        /** 报价截止时间。 */ private LocalDateTime quotationDeadline;
        /** 币种。 */ private String currencyCode;
        /** 状态。 */ private String status;
        /** 发送时间。 */ private LocalDateTime sentTime;
        /** 中标供应商 ID。 */ private Long awardedSupplierId;
        /** 中标报价 ID。 */ private Long awardedQuotationId;
        /** 中标报价版本。 */ private Integer awardedQuotationVersion;
        /** 定点时间。 */ private LocalDateTime awardedTime;
        /** 负责人用户 ID。 */ private Long ownerUserId;
        /** 负责人组织 ID。 */ private Long ownerUnitId;
        /** 乐观锁版本。 */ private Integer version;
        /** 创建时间。 */ private LocalDateTime createTime;
        /** 更新时间。 */ private LocalDateTime updateTime;
    }

    /** 询价详情。 */
    @Data
    @EqualsAndHashCode(callSuper = true)
    public static class Detail extends Summary {
        @Serial private static final long serialVersionUID = 1L;
        /** 询价行快照。 */ private List<Line> lines;
        /** 供应商邀请快照。 */ private List<SupplierInvitation> suppliers;
    }

    /** 询价行快照。 */
    @Data
    public static class Line implements Serializable {
        @Serial private static final long serialVersionUID = 1L;
        /** 主键。 */ private Long id;
        /** 行号。 */ private Integer lineNo;
        /** 物料 ID。 */ private Long materialId;
        /** 物料编码快照。 */ private String materialCode;
        /** 物料名称快照。 */ private String materialName;
        /** 品类编码快照。 */ private String categoryCode;
        /** 计量单位快照。 */ private String unit;
        /** 询价数量快照。 */
        @com.fasterxml.jackson.databind.annotation.JsonSerialize(
                using = com.fasterxml.jackson.databind.ser.std.ToStringSerializer.class)
        @tools.jackson.databind.annotation.JsonSerialize(
                using = tools.jackson.databind.ser.std.ToStringSerializer.class)
        private BigDecimal quantity;
        /** 采购方备注。 */ private String remark;
        /** 乐观锁版本。 */ private Integer version;
    }

    /** 供应商邀请快照。 */
    @Data
    public static class SupplierInvitation implements Serializable {
        @Serial private static final long serialVersionUID = 1L;
        /** 主键。 */ private Long id;
        /** 供应商 ID。 */ private Long supplierId;
        /** 邀请时供应商名称。 */ private String supplierName;
        /** 邀请时间。 */ private LocalDateTime invitedTime;
        /** SRM 报价 ID。 */ private Long quotationId;
        /** SRM 报价版本。 */ private Integer quotationVersion;
        /** 最近报价时间。 */ private LocalDateTime quotationTime;
        /** 邀请状态。 */ private String status;
        /** 乐观锁版本。 */ private Integer version;
    }

    /** 创建询价时使用的合格供应商选项，不包含 PII。 */
    @Data
    public static class SupplierOption implements Serializable {
        @Serial private static final long serialVersionUID = 1L;
        /** 供应商 ID。 */ private Long id;
        /** 供应商编号。 */ private String supplierNo;
        /** 供应商名称。 */ private String name;
        /** 供应商等级。 */ private String levelCode;
        /** 供应品类。 */ private String categoryCode;
    }

    /** 定点结果，明确返回已更新 RFQ 与新建采购订单。 */
    @Data
    public static class AwardResult implements Serializable {
        @Serial private static final long serialVersionUID = 1L;
        /** 定点后的 RFQ 详情。 */ private Detail rfq;
        /** 由中标报价生成的采购订单详情。 */ private PurchaseOrderViews.Detail purchaseOrder;
    }

    /** 提供给 SRM 的无 PII 邀请摘要。 */
    @Data
    public static class InternalInvitationSummary implements Serializable {
        @Serial private static final long serialVersionUID = 1L;
        /** 租户 ID。 */ private Long tenantId;
        /** RFQ ID。 */ private Long rfqId;
        /** RFQ 编号。 */ private String rfqNo;
        /** RFQ 标题。 */ private String title;
        /** RFQ 状态。 */ private String status;
        /** 当前供应商邀请状态。 */ private String invitationStatus;
        /** 受邀供应商 ID。 */ private Long supplierId;
        /** 报价截止时间。 */ private LocalDateTime quotationDeadline;
        /** RFQ 币种。 */ private String currencyCode;
        /** 邀请时间。 */ private LocalDateTime invitedTime;
    }

    /** 提供给 SRM 的无 PII 邀请详情。 */
    @Data
    @EqualsAndHashCode(callSuper = true)
    public static class InternalInvitationDetail extends InternalInvitationSummary {
        @Serial private static final long serialVersionUID = 1L;
        /** RFQ 行快照。 */ private List<InternalInvitationLine> lines;
    }

    /** 提供给 SRM 的 RFQ 行快照。 */
    @Data
    public static class InternalInvitationLine implements Serializable {
        @Serial private static final long serialVersionUID = 1L;
        /** RFQ 行 ID。 */ private Long rfqLineId;
        /** 物料编码快照。 */ private String materialCode;
        /** 物料名称快照。 */ private String materialName;
        /** 计量单位快照。 */ private String unit;
        /** 询价数量快照，必须按 JSON 字符串输出。 */
        @com.fasterxml.jackson.databind.annotation.JsonSerialize(
                using = com.fasterxml.jackson.databind.ser.std.ToStringSerializer.class)
        @tools.jackson.databind.annotation.JsonSerialize(
                using = tools.jackson.databind.ser.std.ToStringSerializer.class)
        private BigDecimal quantity;
        /** 采购方备注。 */ private String remark;
    }
}
