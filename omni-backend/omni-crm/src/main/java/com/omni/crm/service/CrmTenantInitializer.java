package com.omni.crm.service;

/**
 * CRM 租户默认配置幂等初始化服务。
 *
 * @author Omni-Stack Team
 */
public interface CrmTenantInitializer {

    /**
     * 确保当前租户已初始化，并返回默认管道 ID。
     *
     * @return 默认管道 ID
     */
    Long ensureInitialized();

    /**
     * 获取当前租户强制使用的默认币种。
     *
     * @return ISO 4217 三位币种码
     */
    String currencyCode();
}
