package com.omni.crm.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.io.Serial;
import java.time.LocalDateTime;

/** CRM 跟进活动实体。 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("crm_activity")
public class CrmActivity extends CrmOwnedEntity {
    @Serial private static final long serialVersionUID = 1L;
    /** 访问根类型 */ private String rootType;
    /** 访问根 ID */ private Long rootId;
    /** 联系人 ID */ private Long contactId;
    /** 活动类型 */ private String activityType;
    /** 主题 */ private String subject;
    /** 纯文本内容 */ private String content;
    /** 状态 */ private String status;
    /** 计划开始时间 */ private LocalDateTime plannedStartTime;
    /** 计划结束时间 */ private LocalDateTime plannedEndTime;
    /** 完成时间 */ private LocalDateTime completedTime;
    /** 下一行动时间 */ private LocalDateTime nextActionTime;
    /** 实际执行用户 ID */ private Long performedByUserId;
}
