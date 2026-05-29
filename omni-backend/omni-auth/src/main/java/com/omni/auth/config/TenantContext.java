package com.omni.auth.config;

import org.springframework.stereotype.Component;

/**
 * Tenant context holder for multi-tenant support.
 * <p>Stores the current tenant ID in a ThreadLocal variable.</p>
 */
@Component
public class TenantContext {

    private static final ThreadLocal<Long> CURRENT_TENANT_ID = new ThreadLocal<>();

    public static void setTenantId(Long tenantId) {
        CURRENT_TENANT_ID.set(tenantId);
    }

    public static Long getTenantId() {
        return CURRENT_TENANT_ID.get();
    }

    public static void clear() {
        CURRENT_TENANT_ID.remove();
    }
}
