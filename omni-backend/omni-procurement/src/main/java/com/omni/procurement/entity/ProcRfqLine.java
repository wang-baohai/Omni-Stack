package com.omni.procurement.entity;

import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.annotation.Version;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.io.Serial;
import java.math.BigDecimal;

/**
 * 询价行，只保存已审批请购行的不可变业务快照。
 *
 * @author Omni-Stack Team
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("proc_rfq_line")
public class ProcRfqLine extends ProcTenantEntity {

    @Serial
    private static final long serialVersionUID = 1L;

    /** 行号。 */
    private Integer lineNo;

    /** 询价单 ID。 */
    private Long rfqId;

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

    /** 询价数量快照。 */
    private BigDecimal quantity;

    /** 采购方备注快照。 */
    private String remark;

    /** 乐观锁版本。 */
    @Version
    private Integer version;

    /** 逻辑删除标记。 */
    @TableLogic
    private Integer deleted;
}
