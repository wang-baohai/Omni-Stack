package com.omni.srm.security;

import com.omni.common.service.persistence.TenantTablePolicy;
import org.springframework.stereotype.Component;

/**
 * SRM 租户表边界，只允许公共 Starter 拦截 SRM 领域表。
 *
 * @author Omni-Stack Team
 */
@Component
public class SrmTenantTablePolicy implements TenantTablePolicy {

    /** {@inheritDoc} */
    @Override
    public boolean appliesTo(String tableName) {
        return tableName != null && tableName.toLowerCase().startsWith("srm_");
    }
}
