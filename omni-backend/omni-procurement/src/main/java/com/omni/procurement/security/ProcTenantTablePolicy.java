package com.omni.procurement.security;

import com.omni.common.service.persistence.TenantTablePolicy;
import org.springframework.stereotype.Component;

/**
 * Procurement 租户表边界，只允许公共 Starter 拦截采购领域表。
 *
 * @author Omni-Stack Team
 */
@Component
public class ProcTenantTablePolicy implements TenantTablePolicy {

    /** {@inheritDoc} */
    @Override
    public boolean appliesTo(String tableName) {
        return tableName != null && tableName.toLowerCase().startsWith("proc_");
    }
}
