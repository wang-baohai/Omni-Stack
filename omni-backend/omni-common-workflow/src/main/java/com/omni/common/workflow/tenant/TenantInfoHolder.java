package com.omni.common.workflow.tenant;

/**
 * 多租户信息持有者（ThreadLocal）。
 * <p>
 * 在请求生命周期内保存当前租户 ID，供 Flowable 引擎在部署和运行时
 * 自动注入 {@code TENANT_ID_} 字段，实现流程数据的租户隔离。</p>
 * <p>
 * 使用方式：</p>
 * <ul>
 *   <li>由 {@link TenantInfoFilter} 在请求入口设置</li>
 *   <li>业务代码通过 {@link #getTenantId()} 获取当前租户</li>
 *   <li>必须在 {@code finally} 块中调用 {@link #clear()} 防止内存泄漏</li>
 * </ul>
 *
 * @author Omni-Stack Team
 */
public final class TenantInfoHolder {

    private static final ThreadLocal<String> TENANT_ID = new ThreadLocal<>();

    private TenantInfoHolder() {
        // 工具类禁止实例化
    }

    /**
     * 设置当前线程的租户 ID。
     *
     * @param tenantId 租户 ID
     */
    public static void setTenantId(String tenantId) {
        TENANT_ID.set(tenantId);
    }

    /**
     * 获取当前线程的租户 ID。
     *
     * @return 租户 ID，未设置时返回 {@code null}
     */
    public static String getTenantId() {
        return TENANT_ID.get();
    }

    /**
     * 清除当前线程的租户 ID，防止 ThreadLocal 内存泄漏。
     * <p>必须在请求结束的 {@code finally} 块中调用。</p>
     */
    public static void clear() {
        TENANT_ID.remove();
    }
}
