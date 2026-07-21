package com.omni.srm.entity;

import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.annotation.Version;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.io.Serial;
import java.math.BigDecimal;

/**
 * SRM 评估评分明细实体。
 *
 * @author Omni-Stack Team
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("srm_evaluation_item")
public class SrmEvaluationItem extends SrmTenantEntity {
    @Serial private static final long serialVersionUID = 1L;
    /** 评估 ID */ private Long evaluationId;
    /** 维度 ID */ private Long dimensionId;
    /** 指标名称 */ private String indicatorName;
    /** 评分 1-5 */ private BigDecimal score;
    /** 权重快照 */ private BigDecimal weight;
    /** 备注 */ private String remark;
    /** 乐观锁版本 */ @Version private Integer version;
    /** 逻辑删除标记 */ @TableLogic private Integer deleted;
}
