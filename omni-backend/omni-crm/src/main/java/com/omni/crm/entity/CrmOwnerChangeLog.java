package com.omni.crm.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.io.Serial;
import java.time.LocalDateTime;

/** CRM 负责人变更不可变历史实体。 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("crm_owner_change_log")
public class CrmOwnerChangeLog extends CrmTenantEntity {
    @Serial private static final long serialVersionUID = 1L;
    /** 实体类型 */ private String entityType;
    /** 实体 ID */ private Long entityId;
    /** 原负责人用户 ID */ private Long oldOwnerUserId;
    /** 原负责人组织 ID */ private Long oldOwnerUnitId;
    /** 新负责人用户 ID */ private Long newOwnerUserId;
    /** 新负责人组织 ID */ private Long newOwnerUnitId;
    /** 操作类型 */ private String operationType;
    /** 原因 */ private String reason;
    /** 操作用户 ID */ private Long operatorUserId;
    /** 操作时间 */ private LocalDateTime operatedTime;
}
