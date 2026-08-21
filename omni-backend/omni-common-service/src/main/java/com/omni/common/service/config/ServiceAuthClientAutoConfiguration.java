package com.omni.common.service.config;

import com.omni.common.service.client.AuthDataScopeResolver;
import com.omni.common.service.client.AuthSecuritySettingsClient;
import com.omni.common.service.client.AuthXssSettingsResolver;
import com.omni.common.service.datascope.DataScopeResolver;
import com.omni.common.service.internal.InternalFeignHeadersFactory;
import com.omni.common.service.xss.XssSettingsResolver;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.cloud.openfeign.EnableFeignClients;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.context.annotation.Bean;

/**
 * Auth 权威数据范围与 XSS 设置内部客户端自动配置。
 *
 * @author Omni-Stack Team
 */
@AutoConfiguration
@ConditionalOnClass(FeignClient.class)
@ConditionalOnExpression("'${omni.service.data-scope.enabled:false}' == 'true' || "
        + "'${omni.service.xss.enabled:false}' == 'true'")
@EnableFeignClients(clients = AuthSecuritySettingsClient.class)
public class ServiceAuthClientAutoConfiguration {

    /**
     * 创建按 client 显式使用的内部请求头工厂。
     *
     * @return 请求头工厂
     */
    @Bean
    @ConditionalOnMissingBean
    public InternalFeignHeadersFactory internalFeignHeadersFactory() {
        return new InternalFeignHeadersFactory();
    }

    /**
     * 创建默认 Auth 数据范围解析器。
     *
     * @param client Auth 客户端
     * @return 数据范围解析器
     */
    @Bean
    @ConditionalOnMissingBean(DataScopeResolver.class)
    public DataScopeResolver dataScopeResolver(AuthSecuritySettingsClient client) {
        return new AuthDataScopeResolver(client);
    }

    /**
     * 创建默认 Auth XSS 设置解析器。
     *
     * @param client Auth 客户端
     * @return XSS 设置解析器
     */
    @Bean
    @ConditionalOnMissingBean(XssSettingsResolver.class)
    public XssSettingsResolver xssSettingsResolver(AuthSecuritySettingsClient client) {
        return new AuthXssSettingsResolver(client);
    }
}
