package com.omni.asset.security;

import com.omni.common.service.persistence.TenantTablePolicy;
import org.springframework.stereotype.Component;

/**
 * Asset 租户表边界，只允许公共 Starter 拦截资产领域表。
 *
 * @author Omni-Stack Team
 */
@Component
public class AssetTenantTablePolicy implements TenantTablePolicy {

    /** {@inheritDoc} */
    @Override
    public boolean appliesTo(String tableName) {
        return tableName != null && tableName.toLowerCase().startsWith("ast_");
    }
}
