package com.omni.common.service.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.omni.common.service.datascope.DataScopeResolver;
import com.omni.common.service.datascope.ServiceDataScopeAspect;
import com.omni.common.service.identity.GatewayPreAuthenticationFilter;
import com.omni.common.service.identity.ServiceIdentityFilter;
import com.omni.common.service.identity.ServicePathPolicy;
import com.omni.common.service.internal.FailClosedInternalTenantResolver;
import com.omni.common.service.internal.InternalApiAuthenticationFilter;
import com.omni.common.service.internal.InternalTenantResolver;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.security.web.SecurityFilterChain;

/**
 * Servlet 业务服务身份、租户上下文和内部认证自动配置。
 *
 * @author Omni-Stack Team
 */
@AutoConfiguration
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
@EnableConfigurationProperties(ServiceIdentityProperties.class)
public class ServiceCoreAutoConfiguration {

    /**
     * 创建路径策略。
     *
     * @param properties 服务属性
     * @return 路径策略
     */
    @Bean
    @ConditionalOnMissingBean
    public ServicePathPolicy servicePathPolicy(ServiceIdentityProperties properties) {
        return new ServicePathPolicy(properties);
    }

    /**
     * 创建失败关闭配置校验器。
     *
     * @param properties 服务属性
     * @return 配置校验器
     */
    @Bean
    @ConditionalOnMissingBean
    public ServiceConfigurationValidator serviceConfigurationValidator(ServiceIdentityProperties properties) {
        return new ServiceConfigurationValidator(properties);
    }

    /**
     * 创建 Gateway 预认证过滤器。
     *
     * @param properties 服务属性
     * @param pathPolicy 路径策略
     * @param objectMapper JSON 映射器
     * @return 预认证过滤器
     */
    @Bean
    @ConditionalOnClass(SecurityFilterChain.class)
    @ConditionalOnProperty(prefix = "omni.service.gateway-preauth", name = "enabled", havingValue = "true")
    @ConditionalOnMissingBean
    public GatewayPreAuthenticationFilter gatewayPreAuthenticationFilter(
            ServiceIdentityProperties properties, ServicePathPolicy pathPolicy, ObjectMapper objectMapper) {
        return new GatewayPreAuthenticationFilter(properties, pathPolicy, objectMapper);
    }

    /**
     * 创建请求身份过滤器。
     *
     * @param pathPolicy 路径策略
     * @param objectMapper JSON 映射器
     * @return 身份过滤器
     */
    @Bean
    @ConditionalOnProperty(prefix = "omni.service.gateway-preauth", name = "enabled", havingValue = "true")
    @ConditionalOnMissingBean
    public ServiceIdentityFilter serviceIdentityFilter(ServicePathPolicy pathPolicy, ObjectMapper objectMapper) {
        return new ServiceIdentityFilter(pathPolicy, objectMapper);
    }

    /**
     * 禁止身份过滤器被 Servlet 容器自动重复注册，服务安全链负责其固定顺序。
     *
     * @param filter Gateway 预认证过滤器
     * @return 禁用的容器注册
     */
    @Bean
    @ConditionalOnProperty(prefix = "omni.service.gateway-preauth", name = "enabled", havingValue = "true")
    @ConditionalOnMissingBean(name = "gatewayPreAuthenticationFilterRegistration")
    public FilterRegistrationBean<GatewayPreAuthenticationFilter> gatewayPreAuthenticationFilterRegistration(
            GatewayPreAuthenticationFilter filter) {
        FilterRegistrationBean<GatewayPreAuthenticationFilter> registration = new FilterRegistrationBean<>(filter);
        registration.setEnabled(false);
        return registration;
    }

    /**
     * 禁止身份上下文过滤器被 Servlet 容器自动重复注册。
     *
     * @param filter 身份上下文过滤器
     * @return 禁用的容器注册
     */
    @Bean
    @ConditionalOnProperty(prefix = "omni.service.gateway-preauth", name = "enabled", havingValue = "true")
    @ConditionalOnMissingBean(name = "serviceIdentityFilterRegistration")
    public FilterRegistrationBean<ServiceIdentityFilter> serviceIdentityFilterRegistration(ServiceIdentityFilter filter) {
        FilterRegistrationBean<ServiceIdentityFilter> registration = new FilterRegistrationBean<>(filter);
        registration.setEnabled(false);
        return registration;
    }

    /**
     * 创建内部 API 认证过滤器。
     *
     * @param properties 服务属性
     * @param pathPolicy 路径策略
     * @param objectMapper JSON 映射器
     * @return 内部认证过滤器
     */
    @Bean
    @ConditionalOnProperty(prefix = "omni.service.internal-api", name = "enabled", havingValue = "true")
    @ConditionalOnMissingBean
    public InternalApiAuthenticationFilter internalApiAuthenticationFilter(
            ServiceIdentityProperties properties, ServicePathPolicy pathPolicy, ObjectMapper objectMapper) {
        return new InternalApiAuthenticationFilter(properties, pathPolicy, objectMapper);
    }

    /**
     * 自动注册内部 API 认证过滤器。
     *
     * @param filter 内部认证过滤器
     * @param properties 服务属性
     * @return Filter 注册信息
     */
    @Bean(name = "serviceInternalApiAuthFilterRegistration")
    @ConditionalOnProperty(prefix = "omni.service.internal-api", name = "enabled", havingValue = "true")
    @ConditionalOnMissingBean(name = "serviceInternalApiAuthFilterRegistration")
    public FilterRegistrationBean<InternalApiAuthenticationFilter> serviceInternalApiAuthFilterRegistration(
            InternalApiAuthenticationFilter filter, ServiceIdentityProperties properties) {
        FilterRegistrationBean<InternalApiAuthenticationFilter> registration = new FilterRegistrationBean<>(filter);
        registration.setOrder(-200);
        registration.setName("serviceInternalApiAuthFilter");
        registration.addUrlPatterns(properties.getInternalPaths().stream()
                .map(path -> path.endsWith("/") ? path + "*" : path + "/*")
                .toArray(String[]::new));
        return registration;
    }

    /**
     * 创建显式租户解析器。
     *
     * @return 失败关闭解析器
     */
    @Bean
    @ConditionalOnMissingBean
    public InternalTenantResolver internalTenantResolver() {
        return new FailClosedInternalTenantResolver();
    }

    /**
     * 创建数据权限切面；解析器缺失时容器启动失败。
     *
     * @param resolver 权威范围解析器
     * @return 数据权限切面
     */
    @Bean
    @ConditionalOnProperty(prefix = "omni.service.data-scope", name = "enabled", havingValue = "true")
    @ConditionalOnMissingBean
    public ServiceDataScopeAspect serviceDataScopeAspect(DataScopeResolver resolver) {
        return new ServiceDataScopeAspect(resolver);
    }
}
