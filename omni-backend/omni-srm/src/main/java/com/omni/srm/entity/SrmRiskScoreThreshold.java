package com.omni.srm.entity;

import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.io.Serial;

/**
 * SRM 风险得分阈值实体。
 * <p>将综合得分映射到风险等级的配置。</p>
 *
 * @author Omni-Stack Team
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("srm_risk_score_threshold")
public class SrmRiskScoreThreshold extends SrmTenantEntity {
    @Serial private static final long serialVersionUID = 1L;
    /** 风险等级 */ private String riskLevel;
    /** 最小分（含） */ private Integer minScore;
    /** 最大分（含） */ private Integer maxScore;
    /** 逻辑删除标记 */ @TableLogic private Integer deleted;
}
