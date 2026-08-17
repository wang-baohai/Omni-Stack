package com.omni.procurement.entity;

import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.annotation.Version;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.io.Serial;
import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * 采购订单不可变中标报价行快照。
 *
 * @author Omni-Stack Team
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("proc_purchase_order_line")
public class ProcPurchaseOrderLine extends ProcTenantEntity {

    @Serial
    private static final long serialVersionUID = 1L;

    /** 行号。 */
    private Integer lineNo;

    /** 采购订单 ID。 */
    private Long poId;

    /** 来源 RFQ 行 ID。 */
    private Long rfqLineId;

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

    /** 中标数量。 */
    private BigDecimal quantity;

    /** 中标单价。 */
    private BigDecimal unitPrice;

    /** 服务端核算的中标行金额。 */
    private BigDecimal totalPrice;

    /** 报价交付天数。 */
    private Integer deliveryDays;

    /** 预计交付日期。 */
    private LocalDate expectedDeliveryDate;

    /** 行备注快照。 */
    private String remark;

    /** 乐观锁版本。 */
    @Version
    private Integer version;

    /** 逻辑删除标记。 */
    @TableLogic
    private Integer deleted;
}
