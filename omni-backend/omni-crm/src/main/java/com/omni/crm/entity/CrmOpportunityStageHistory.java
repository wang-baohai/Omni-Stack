package com.omni.crm.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.io.Serial;
import java.time.LocalDateTime;

/** CRM 商机阶段变更历史实体。 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("crm_opportunity_stage_history")
public class CrmOpportunityStageHistory extends CrmTenantEntity {
    @Serial private static final long serialVersionUID = 1L;
    /** 商机 ID */ private Long opportunityId;
    /** 原阶段 ID */ private Long fromStageId;
    /** 新阶段 ID */ private Long toStageId;
    /** 原状态 */ private String fromStatus;
    /** 新状态 */ private String toStatus;
    /** 变更原因 */ private String changeReason;
    /** 操作用户 ID */ private Long changedByUserId;
    /** 变更时间 */ private LocalDateTime changedTime;
}
