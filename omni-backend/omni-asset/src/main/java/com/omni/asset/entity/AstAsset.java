package com.omni.asset.entity;

import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.annotation.Version;
import com.omni.common.core.model.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.io.Serial;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 资产台账聚合根。
 *
 * @author Omni-Stack Team
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("ast_asset")
public class AstAsset extends BaseEntity {

    @Serial
    private static final long serialVersionUID = 1L;

    /** 租户 ID。 */
    private Long tenantId;

    /** 租户内唯一资产编号。 */
    private String assetNo;

    /** 资产名称。 */
    private String name;

    /** 资产品类字典编码。 */
    private String categoryCode;

    /** 规格描述。 */
    private String specification;

    /** 品牌。 */
    private String brand;

    /** 型号。 */
    private String model;

    /** 供应商 ID。 */
    private Long supplierId;

    /** 入库时供应商名称快照。 */
    private String supplierNameSnapshot;

    /** 来源采购订单 ID。 */
    private Long sourcePoId;

    /** 来源收货单 ID。 */
    private Long sourceGrId;

    /** 来源收货行 ID。 */
    private Long sourceGrLineId;

    /** 来源收货行内单位序号。 */
    private Integer sourceUnitSequence;

    /** 来源采购订单号快照。 */
    private String sourcePoNo;

    /** 来源收货单号快照。 */
    private String sourceGrNo;

    /** 采购日期。 */
    private LocalDate purchaseDate;

    /** 资产采购原值。 */
    private BigDecimal purchaseAmount;

    /** ISO 4217 币种编码。 */
    private String currencyCode;

    /** 资产位置字典编码。 */
    private String locationCode;

    /** 资产生命周期状态。 */
    private String status;

    /** 当前使用人 ID。 */
    private Long currentUserId;

    /** 当前使用部门 ID。 */
    private Long currentUnitId;

    /** 最近分配时间。 */
    private LocalDateTime allocatedTime;

    /** 当前活动操作类型。 */
    private String activeOperationType;

    /** 当前活动操作 ID。 */
    private Long activeOperationId;

    /** 保修到期日。 */
    private LocalDate warrantyExpiryDate;

    /** 预期使用年限。 */
    private Integer expectedLifeYears;

    /** 纯文本备注。 */
    private String remark;

    /** 资产管理员用户 ID。 */
    private Long ownerUserId;

    /** 资产管理部门 ID。 */
    private Long ownerUnitId;

    /** 乐观锁版本。 */
    @Version
    private Integer version;

    /** 逻辑删除标记。 */
    @TableLogic
    private Integer deleted;
}
