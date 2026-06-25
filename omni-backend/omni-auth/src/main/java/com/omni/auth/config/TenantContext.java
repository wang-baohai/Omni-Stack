package com.omni.auth.config;

import org.springframework.stereotype.Component;

/**
 * 租户上下文持有者，用于多租户支持。
 * <p>
 * 通过 {@link ThreadLocal} 变量存储当前线程的租户 ID，
 * 在请求处理链路中传递租户信息。典型使用场景：</p>
 * <ul>
 *   <li>{@link com.omni.auth.security.DataPermissionHandlerImpl} — 数据权限拦截器从本类获取租户 ID 追加 SQL 条件</li>
 *   <li>Controller/Service 层 — 创建数据时自动填充 {@code tenant_id} 字段</li>
 * </ul>
 *
 * <p><b>重要：</b>必须在请求结束时调用 {@link #clear()} 清理 ThreadLocal，
 * 防止线程复用时的租户数据泄露。</p>
 *
 * @see com.omni.auth.security.DataPermissionHandlerImpl
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
