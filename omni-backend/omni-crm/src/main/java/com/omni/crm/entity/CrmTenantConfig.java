package com.omni.crm.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.io.Serial;
import java.time.LocalDateTime;

/** CRM 租户级默认配置实体。 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("crm_tenant_config")
public class CrmTenantConfig extends CrmTenantEntity {
    @Serial private static final long serialVersionUID = 1L;
    /** 默认销售管道 ID */ private Long defaultPipelineId;
    /** 默认币种 */ private String currencyCode;
    /** 线索重复策略 */ private String leadDuplicatePolicy;
    /** 初始化时间 */ private LocalDateTime initializedTime;
}
