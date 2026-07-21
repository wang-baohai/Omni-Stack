package com.omni.srm.service;

/**
 * SRM 租户默认配置幂等初始化服务。
 *
 * @author Omni-Stack Team
 */
public interface SrmTenantInitializer {

    /**
     * 确保当前租户已创建 MVP 默认评估模板及维度。
     *
     * @return 默认评估模板 ID
     */
    Long ensureInitialized();
}
