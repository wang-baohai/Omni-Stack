package com.omni.procurement.entity;

import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.annotation.Version;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.io.Serial;
import java.math.BigDecimal;

/**
 * 请购申请明细行，保存提交时的物料主数据快照。
 *
 * @author Omni-Stack Team
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("proc_requisition_line")
public class ProcRequisitionLine extends ProcTenantEntity {

    @Serial
    private static final long serialVersionUID = 1L;

    /** 行号。 */
    private Integer lineNo;

    /** 请购申请 ID。 */
    private Long requisitionId;

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

    /** 请购数量。 */
    private BigDecimal quantity;

    /** 预估单价。 */
    private BigDecimal estimatedUnitPrice;

    /** 服务端计算的预估行金额。 */
    private BigDecimal estimatedTotalPrice;

    /** 行备注。 */
    private String remark;

    /** 乐观锁版本。 */
    @Version
    private Integer version;

    /** 逻辑删除标记。 */
    @TableLogic
    private Integer deleted;
}
