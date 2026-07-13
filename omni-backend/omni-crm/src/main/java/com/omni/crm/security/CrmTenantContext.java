package com.omni.crm.security;

import com.omni.common.core.result.BusinessException;

/**
 * CRM 租户与调用人上下文。
 *
 * @author Omni-Stack Team
 */
public final class CrmTenantContext {

    private static final ThreadLocal<RequestIdentity> CONTEXT = new ThreadLocal<>();

    private CrmTenantContext() {
    }

    /**
     * 设置当前请求身份。
     *
     * @param identity 请求身份
     */
    public static void set(RequestIdentity identity) {
        CONTEXT.set(identity);
    }

    /**
     * 获取当前请求身份，缺失时失败关闭。
     *
     * @return 请求身份
     */
    public static RequestIdentity require() {
        RequestIdentity identity = CONTEXT.get();
        if (identity == null || identity.tenantId() == null || identity.userId() == null) {
            throw new BusinessException(403, "缺少 CRM 租户或用户上下文");
        }
        return identity;
    }

    /**
     * 获取当前租户 ID，缺失时失败关闭。
     *
     * @return 租户 ID
     */
    public static Long requireTenantId() {
        return require().tenantId();
    }

    /**
     * 清理当前线程上下文。
     */
    public static void clear() {
        CONTEXT.remove();
    }

    /**
     * 请求身份快照。
     *
     * @param userId 用户 ID
     * @param tenantId 租户 ID
     * @param username 用户名
     */
    public record RequestIdentity(Long userId, Long tenantId, String username) {
    }
}
