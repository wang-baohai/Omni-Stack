package com.omni.common.security.xss;

import com.fasterxml.jackson.databind.module.SimpleModule;
import com.omni.common.core.security.XssConfigProvider;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.core.Ordered;

/**
 * XSS 防护自动配置。
 * <p>
 * 仅在 Servlet Web 环境下生效。当容器中存在 {@link XssConfigProvider} 实现时，
 * 自动注册 XSS Servlet 过滤器和 Jackson 反序列化模块。
 * </p>
 * <p>
 * Jackson 集成通过 {@link SimpleModule} 注册 {@link XssStringDeserializer}，
 * 消费方服务的 Jackson 2.x ObjectMapper 会自动发现并注册该模块。
 * </p>
 */
@AutoConfiguration
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
public class XssAutoConfiguration {

    /**
     * 注册 XSS Servlet 过滤器。
     * <p>过滤所有请求路径，优先级设置为 {@link Ordered#HIGHEST_PRECEDENCE} + 10。</p>
     *
     * @param xssConfigProvider XSS 配置提供者
     * @return 过滤器注册 Bean
     */
    @Bean
    @ConditionalOnBean(XssConfigProvider.class)
    public FilterRegistrationBean<XssFilter> xssFilterRegistration(XssConfigProvider xssConfigProvider) {
        FilterRegistrationBean<XssFilter> registration = new FilterRegistrationBean<>();
        registration.setFilter(new XssFilter(xssConfigProvider));
        registration.addUrlPatterns("/*");
        registration.setOrder(Ordered.HIGHEST_PRECEDENCE + 10);
        registration.setName("xssFilter");
        return registration;
    }

    /**
     * 注册 Jackson Module，注入 XSS 感知的 String 反序列化器。
     * <p>
     * 通过 {@link SimpleModule} 注册 {@link XssStringDeserializer}，
     * 消费方服务的 Jackson 2.x {@code ObjectMapper} 会自动发现并注册该模块，
     * 对所有 String 类型字段在反序列化时执行 XSS 净化。
     * </p>
     *
     * @return XSS 防护 Jackson 模块
     */
    @Bean
    @ConditionalOnBean(XssConfigProvider.class)
    @ConditionalOnClass(name = "com.fasterxml.jackson.databind.ObjectMapper")
    public SimpleModule xssJacksonModule() {
        SimpleModule module = new SimpleModule("XssModule");
        module.addDeserializer(String.class, new XssStringDeserializer(
                new com.fasterxml.jackson.databind.deser.std.StringDeserializer()));
        return module;
    }
}
