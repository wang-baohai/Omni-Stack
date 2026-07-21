package com.omni.srm.entity;

import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.io.Serial;
import java.math.BigDecimal;

/**
 * SRM 评估维度实体。
 *
 * @author Omni-Stack Team
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("srm_evaluation_dimension")
public class SrmEvaluationDimension extends SrmTenantEntity {
    @Serial private static final long serialVersionUID = 1L;
    /** 模板 ID */ private Long templateId;
    /** 指标名称 */ private String indicatorName;
    /** 权重百分比 */ private BigDecimal weight;
    /** 排序 */ private Integer sort;
    /** 状态 */ private Integer status;
    /** 逻辑删除标记 */ @TableLogic private Integer deleted;
}
