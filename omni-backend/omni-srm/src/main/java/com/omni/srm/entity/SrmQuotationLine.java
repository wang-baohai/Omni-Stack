package com.omni.srm.entity;

import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.annotation.Version;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.io.Serial;
import java.math.BigDecimal;

/**
 * SRM 供应商报价明细实体。
 *
 * @author Omni-Stack Team
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("srm_quotation_line")
public class SrmQuotationLine extends SrmTenantEntity {

    @Serial
    private static final long serialVersionUID = 1L;

    /** 报价头 ID。 */
    private Long quotationId;

    /** Procurement 询价行 ID。 */
    private Long rfqLineId;

    /** 物料编码快照。 */
    private String materialCode;

    /** 物料名称快照。 */
    private String materialName;

    /** 计量单位快照。 */
    private String unit;

    /** 含税或未税单价，由 RFQ 约定解释。 */
    private BigDecimal unitPrice;

    /** 询价数量快照。 */
    private BigDecimal quantity;

    /** 服务端计算的行金额。 */
    private BigDecimal lineAmount;

    /** 交付天数。 */
    private Integer deliveryDays;

    /** 供应商备注。 */
    private String remark;

    /** 乐观锁版本。 */
    @Version
    private Integer version;

    /** 逻辑删除标记。 */
    @TableLogic
    private Integer deleted;
}
