package com.omni.srm.entity;

import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.annotation.Version;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.io.Serial;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * SRM 绩效评估实体。
 *
 * @author Omni-Stack Team
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("srm_evaluation")
public class SrmEvaluation extends SrmOwnedEntity {
    @Serial private static final long serialVersionUID = 1L;
    /** 供应商 ID */ private Long supplierId;
    /** 模板 ID */ private Long templateId;
    /** 评估周期 */ private String evaluationPeriod;
    /** 百分制总分 */ private BigDecimal totalScore;
    /** 评估人用户 ID */ private Long evaluatorUserId;
    /** 评估时间 */ private LocalDateTime evaluationTime;
    /** 状态 */ private String status;
}
