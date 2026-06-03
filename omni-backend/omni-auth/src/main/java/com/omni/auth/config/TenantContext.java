package com.omni.auth.config;

import org.springframework.stereotype.Component;

/**
 * 租户上下文持有者，用于多租户支持。
 * <p>通过 ThreadLocal 变量存储当前线程的租户 ID。</p>
 */
@Component
public class TenantContext {

    /** 当前线程的租户 ID，使用 ThreadLocal 隔离 */
    private static final ThreadLocal<Long> CURRENT_TENANT_ID = new ThreadLocal<>();

    /**
     * 设置当前线程的租户 ID。
     *
     * @param tenantId 租户 ID
     */
    public static void setTenantId(Long tenantId) {
        CURRENT_TENANT_ID.set(tenantId);
    }

    /**
     * 获取当前线程的租户 ID。
     *
     * @return 租户 ID，未设置时返回 null
     */
    public static Long getTenantId() {
        return CURRENT_TENANT_ID.get();
    }

    /**
     * 清除当前线程的租户 ID，防止内存泄漏。
     */
    public static void clear() {
        CURRENT_TENANT_ID.remove();
    }
}
