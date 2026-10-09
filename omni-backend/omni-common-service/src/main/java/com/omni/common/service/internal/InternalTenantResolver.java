package com.omni.common.service.internal;

/**
 * 内部消息或任务显式租户解析策略。
 *
 * @author Omni-Stack Team
 */
@FunctionalInterface
public interface InternalTenantResolver {

    /**
     * 校验并返回显式租户 ID；禁止从请求 ThreadLocal 猜测。
     *
     * @param explicitTenantId 调用方显式传入的租户 ID
     * @return 合法租户 ID
     */
    Long requireExplicitTenantId(Long explicitTenantId);
}
