package com.omni.procurement.service;

/**
 * 采购租户默认数据初始化服务。
 *
 * @author Omni-Stack Team
 */
public interface ProcTenantInitializer {

    /**
     * 幂等初始化默认品类和租户配置。
     */
    void ensureInitialized();

    /**
     * 获取当前租户默认币种。
     *
     * @return ISO 4217 三位币种码
     */
    String currencyCode();
}
