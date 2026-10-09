package com.omni.crm.security;

import com.omni.common.service.persistence.TenantTablePolicy;
import org.springframework.stereotype.Component;

/**
 * CRM 租户表边界，只允许公共 Starter 拦截 CRM 领域表。
 *
 * @author Omni-Stack Team
 */
@Component
public class CrmTenantTablePolicy implements TenantTablePolicy {

    /** {@inheritDoc} */
    @Override
    public boolean appliesTo(String tableName) {
        return tableName != null && tableName.toLowerCase().startsWith("crm_");
    }
}
