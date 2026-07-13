package com.omni.crm.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.io.Serial;
import java.time.LocalDateTime;

/** CRM 线索转换幂等事实实体。 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("crm_lead_conversion")
public class CrmLeadConversion extends CrmTenantEntity {
    @Serial private static final long serialVersionUID = 1L;
    /** 线索 ID */ private Long leadId;
    /** 客户 ID */ private Long customerId;
    /** 联系人 ID */ private Long contactId;
    /** 商机 ID */ private Long opportunityId;
    /** 转换用户 ID */ private Long convertedByUserId;
    /** 转换时间 */ private LocalDateTime convertedTime;
}
