package com.omni.common.service.internal;

import feign.RequestInterceptor;
import org.slf4j.MDC;

import java.util.function.Supplier;

import com.omni.common.web.TraceIdFilter;

/**
 * 为单个 Feign client 创建内部认证、显式租户和 Trace 传播拦截器。
 *
 * <p>本工厂不注册全局 Token 拦截器，调用方必须在目标 client 的 configuration 中显式使用。</p>
 *
 * @author Omni-Stack Team
 */
public class InternalFeignHeadersFactory {

    /**
     * 创建只传播内部 Token 与 Trace 的拦截器。
     *
     * @param token 内部服务密钥
     * @return Feign 拦截器
     */
    public RequestInterceptor create(String token) {
        return create(token, () -> null);
    }

    /**
     * 创建传播内部 Token、调用方显式租户与 Trace 的拦截器。
     *
     * @param token 内部服务密钥
     * @param explicitTenantSupplier 显式租户提供器
     * @return Feign 拦截器
     */
    public RequestInterceptor create(String token, Supplier<Long> explicitTenantSupplier) {
        requireToken(token);
        return template -> {
            template.header("X-Internal-Token", token);
            Long tenantId = explicitTenantSupplier.get();
            if (tenantId != null) {
                if (tenantId <= 0) {
                    throw new IllegalStateException("Feign 内部调用租户 ID 必须为正数");
                }
                template.header("X-Tenant-Id", tenantId.toString());
            }
            String traceId = MDC.get(TraceIdFilter.MDC_KEY);
            if (traceId != null && !traceId.isBlank()) {
                template.header(TraceIdFilter.TRACE_HEADER, traceId);
            }
        };
    }

    private void requireToken(String token) {
        if (token == null || token.isBlank()) {
            throw new IllegalStateException("Feign 内部调用 Token 不能为空");
        }
    }
}
