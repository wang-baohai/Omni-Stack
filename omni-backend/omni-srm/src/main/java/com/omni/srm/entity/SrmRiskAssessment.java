package com.omni.srm.entity;

import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.annotation.Version;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.io.Serial;
import java.time.LocalDateTime;

/**
 * SRM 综合风险评估实体。
 *
 * @author Omni-Stack Team
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("srm_risk_assessment")
public class SrmRiskAssessment extends SrmTenantEntity {
    @Serial private static final long serialVersionUID = 1L;
    /** 供应商 ID */ private Long supplierId;
    /** 综合风险等级 RED/YELLOW/GREEN */ private String overallLevel;
    /** 评估时间 */ private LocalDateTime assessmentTime;
    /** 评估人用户 ID */ private Long assessorUserId;
    /** 备注 */ private String remark;
    /** 乐观锁版本 */ @Version private Integer version;
    /** 逻辑删除标记 */ @TableLogic private Integer deleted;
}
