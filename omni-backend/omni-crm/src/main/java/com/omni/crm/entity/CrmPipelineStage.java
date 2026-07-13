package com.omni.crm.entity;

import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.io.Serial;
import java.math.BigDecimal;

/** CRM 销售管道阶段实体。 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("crm_pipeline_stage")
public class CrmPipelineStage extends CrmTenantEntity {
    @Serial private static final long serialVersionUID = 1L;
    /** 管道 ID */ private Long pipelineId;
    /** 阶段编码 */ private String stageCode;
    /** 阶段名称 */ private String stageName;
    /** 阶段类型 */ private String stageType;
    /** 概率 */ private BigDecimal probability;
    /** 排序 */ private Integer sort;
    /** 状态 */ private Integer status;
    /** 逻辑删除标记 */ @TableLogic private Integer deleted;
}
