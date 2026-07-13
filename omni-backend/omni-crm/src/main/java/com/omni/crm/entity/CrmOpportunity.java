package com.omni.crm.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.io.Serial;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/** CRM 商机聚合根实体。 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("crm_opportunity")
public class CrmOpportunity extends CrmOwnedEntity {
    @Serial private static final long serialVersionUID = 1L;
    /** 业务编号 */ private String opportunityNo;
    /** 名称 */ private String name;
    /** 客户 ID */ private Long customerId;
    /** 主要联系人 ID */ private Long primaryContactId;
    /** 来源线索 ID */ private Long sourceLeadId;
    /** 销售管道 ID */ private Long pipelineId;
    /** 当前阶段 ID */ private Long stageId;
    /** 状态 */ private String status;
    /** 金额 */ private BigDecimal amount;
    /** 币种 */ private String currencyCode;
    /** 概率快照 */ private BigDecimal probability;
    /** 预计成交日期 */ private LocalDate expectedCloseDate;
    /** 实际关闭时间 */ private LocalDateTime actualCloseTime;
    /** 输单原因 */ private String lossReason;
    /** 阶段变更时间 */ private LocalDateTime stageChangeTime;
    /** 下次跟进时间 */ private LocalDateTime nextFollowupTime;
}
