package com.omni.procurement.entity;

import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.annotation.Version;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.io.Serial;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 采购收货明细及资产化事件门闩。
 *
 * @author Omni-Stack Team
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("proc_goods_receipt_line")
public class ProcGoodsReceiptLine extends ProcTenantEntity {

    @Serial
    private static final long serialVersionUID = 1L;

    /** 行号。 */
    private Integer lineNo;

    /** 收货单 ID。 */
    private Long goodsReceiptId;

    /** 采购订单行 ID。 */
    private Long poLineId;

    /** 物料 ID。 */
    private Long materialId;

    /** 物料编码快照。 */
    private String materialCode;

    /** 物料名称快照。 */
    private String materialName;

    /** 品类编码快照。 */
    private String categoryCode;

    /** 计量单位快照。 */
    private String unit;

    /** 是否进入资产化判断。 */
    private Boolean assetManaged;

    /** 订单数量快照。 */
    private BigDecimal orderedQuantity;

    /** 本次收货数量。 */
    private BigDecimal receivedQuantity;

    /** PASS/FAIL/PENDING。 */
    private String qualityStatus;

    /** 质检结果时间。 */
    private LocalDateTime qualityResultTime;

    /** 收货确认事件 ID。 */
    private String confirmedEventId;

    /** 后续质检通过事件 ID。 */
    private String qualityPassedEventId;

    /** 行备注。 */
    private String remark;

    /** 乐观锁版本。 */
    @Version
    private Integer version;

    /** 逻辑删除标记。 */
    @TableLogic
    private Integer deleted;
}
