package com.omni.common.service.identity;

import com.omni.common.core.result.BusinessException;

/**
 * Servlet 业务请求身份上下文。
 *
 * @author Omni-Stack Team
 */
public final class ServiceIdentityContext {

    private static final ThreadLocal<ServiceRequestIdentity> CONTEXT = new ThreadLocal<>();

    private ServiceIdentityContext() {
    }

    /**
     * 设置当前请求身份。
     *
     * @param identity 请求身份
     */
    public static void set(ServiceRequestIdentity identity) {
        CONTEXT.set(identity);
    }

    /**
     * 获取当前请求身份，缺失时失败关闭。
     *
     * @return 请求身份
     */
    public static ServiceRequestIdentity require() {
        ServiceRequestIdentity identity = CONTEXT.get();
        if (identity == null || identity.userId() == null || identity.tenantId() == null) {
            throw new BusinessException(403, "缺少服务请求身份上下文");
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
}
