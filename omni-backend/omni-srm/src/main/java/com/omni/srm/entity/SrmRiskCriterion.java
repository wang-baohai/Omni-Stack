package com.omni.srm.entity;

import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.io.Serial;

/**
 * SRM 风险评分标准实体。
 * <p>每个指标类型下的预设评分选项，带分值和对应风险等级。</p>
 *
 * @author Omni-Stack Team
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("srm_risk_criterion")
public class SrmRiskCriterion extends SrmTenantEntity {
    @Serial private static final long serialVersionUID = 1L;
    /** 关联指标类型 ID */ private Long indicatorTypeId;
    /** 评分标准描述 */ private String criterionLabel;
    /** 分值（越高越危险） */ private Integer score;
    /** 对应风险等级 */ private String riskLevel;
    /** 排序 */ private Integer sort;
    /** 状态：1=启用 0=禁用 */ private Integer status;
    /** 逻辑删除标记 */ @TableLogic private Integer deleted;
}
