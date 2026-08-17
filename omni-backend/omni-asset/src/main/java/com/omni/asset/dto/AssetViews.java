package com.omni.asset.dto;

import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 资产台账响应 DTO 集合。
 *
 * @author Omni-Stack Team
 */
public final class AssetViews {

    private AssetViews() {
    }

    /** 资产管理员或使用人候选。 */
    @Data
    public static class UserOptionVO implements Serializable {
        @Serial private static final long serialVersionUID = 1L;
        /** 用户 ID。 */ private Long id;
        /** 登录账号。 */ private String username;
        /** 用户昵称。 */ private String nickname;
        /** 主组织单元 ID。 */ private Long primaryUnitId;
        /** 头像 URL。 */ private String avatar;
    }

    /** 可发起调拨或处置的资产候选。 */
    @Data
    public static class AssetOptionVO implements Serializable {
        @Serial private static final long serialVersionUID = 1L;
        /** 资产 ID。 */ private Long id;
        /** 资产编号。 */ private String assetNo;
        /** 资产名称。 */ private String name;
        /** 当前状态。 */ private String status;
        /** 当前使用人 ID。 */ private Long currentUserId;
        /** 当前使用部门 ID。 */ private Long currentUnitId;
    }

    /** 资产采购来源供应商候选。 */
    @Data
    public static class SupplierOptionVO implements Serializable {
        @Serial private static final long serialVersionUID = 1L;
        /** 供应商 ID。 */ private Long id;
        /** 供应商编号。 */ private String supplierNo;
        /** 供应商名称。 */ private String name;
        /** 生命周期状态。 */ private String status;
        /** 供应商等级。 */ private String levelCode;
        /** 供应品类。 */ private String categoryCode;
    }

    /** 资产列表与详情视图。 */
    @Data
    public static class AssetVO implements Serializable {
        @Serial private static final long serialVersionUID = 1L;
        /** 主键 ID。 */ private Long id;
        /** 资产编号。 */ private String assetNo;
        /** 资产名称。 */ private String name;
        /** 品类编码。 */ private String categoryCode;
        /** 规格。 */ private String specification;
        /** 品牌。 */ private String brand;
        /** 型号。 */ private String model;
        /** 供应商 ID。 */ private Long supplierId;
        /** 供应商名称快照。 */ private String supplierNameSnapshot;
        /** 来源采购订单 ID。 */ private Long sourcePoId;
        /** 来源收货单 ID。 */ private Long sourceGrId;
        /** 来源收货行 ID。 */ private Long sourceGrLineId;
        /** 来源单位序号。 */ private Integer sourceUnitSequence;
        /** 来源采购订单号。 */ private String sourcePoNo;
        /** 来源收货单号。 */ private String sourceGrNo;
        /** 采购日期。 */ private LocalDate purchaseDate;
        /** 采购原值，JSON 中固定输出十进制字符串。 */
        @com.fasterxml.jackson.databind.annotation.JsonSerialize(
                using = com.fasterxml.jackson.databind.ser.std.ToStringSerializer.class)
        @tools.jackson.databind.annotation.JsonSerialize(
                using = tools.jackson.databind.ser.std.ToStringSerializer.class)
        private BigDecimal purchaseAmount;
        /** 币种编码。 */ private String currencyCode;
        /** 位置编码。 */ private String locationCode;
        /** 生命周期状态。 */ private String status;
        /** 当前使用人 ID。 */ private Long currentUserId;
        /** 当前使用部门 ID。 */ private Long currentUnitId;
        /** 最近分配时间。 */ private LocalDateTime allocatedTime;
        /** 当前活动操作类型。 */ private String activeOperationType;
        /** 当前活动操作 ID。 */ private Long activeOperationId;
        /** 保修到期日。 */ private LocalDate warrantyExpiryDate;
        /** 预期使用年限。 */ private Integer expectedLifeYears;
        /** 备注。 */ private String remark;
        /** 资产管理员用户 ID。 */ private Long ownerUserId;
        /** 资产管理部门 ID。 */ private Long ownerUnitId;
        /** 乐观锁版本。 */ private Integer version;
        /** 创建时间。 */ private LocalDateTime createTime;
        /** 更新时间。 */ private LocalDateTime updateTime;
    }

    /** 资产历史视图。 */
    @Data
    public static class HistoryVO implements Serializable {
        @Serial private static final long serialVersionUID = 1L;
        /** 主键 ID。 */ private Long id;
        /** 资产 ID。 */ private Long assetId;
        /** 变更前状态。 */ private String fromStatus;
        /** 变更后状态。 */ private String toStatus;
        /** 变更用户 ID。 */ private Long changedByUserId;
        /** 变更时间。 */ private LocalDateTime changedTime;
        /** 变更说明。 */ private String remark;
    }
}
