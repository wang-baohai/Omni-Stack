package com.omni.common.service.client;

import com.omni.common.core.internal.InternalDataScopeDTO;
import com.omni.common.core.result.R;
import com.omni.common.core.security.XssSettings;
import com.omni.common.service.config.ServiceIdentityProperties;
import com.omni.common.service.internal.InternalFeignHeadersFactory;
import feign.RequestInterceptor;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.context.annotation.Bean;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;

/**
 * Starter 访问 Auth 权威数据范围和 XSS 设置的最小内部客户端。
 *
 * @author Omni-Stack Team
 */
@FeignClient(name = "omni-auth", contextId = "omniServiceAuthSecurityClient",
        configuration = AuthSecuritySettingsClient.FeignConfiguration.class)
public interface AuthSecuritySettingsClient {

    /**
     * 按完整权限码解析数据范围。
     *
     * @param userId 用户 ID
     * @param tenantId 租户 ID
     * @param permissionCode 权限码
     * @return 数据范围响应
     */
    @GetMapping("/internal/data-scopes/{userId}")
    R<InternalDataScopeDTO> resolveDataScope(@PathVariable("userId") Long userId,
                                             @RequestParam("tenantId") Long tenantId,
                                             @RequestParam("permissionCode") String permissionCode);

    /**
     * 获取租户 XSS 权威设置。
     *
     * @param tenantId 租户 ID
     * @return XSS 设置响应
     */
    @GetMapping("/internal/xss/settings")
    R<XssSettings> getXssSettings(@RequestParam("tenantId") Long tenantId);

    /**
     * 只作用于本 Client 的内部请求头配置。
     */
    class FeignConfiguration {

        /**
         * 创建内部认证请求头拦截器。
         *
         * @param properties 服务属性
         * @param factory 请求头工厂
         * @return Feign 请求拦截器
         */
        @Bean
        public RequestInterceptor authSecurityInternalHeaders(
                ServiceIdentityProperties properties, InternalFeignHeadersFactory factory) {
            return factory.create(properties.getInternalApi().getToken());
        }
    }
}
