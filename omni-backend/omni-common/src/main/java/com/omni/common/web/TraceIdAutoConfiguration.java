package com.omni.common.web;

import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.annotation.Bean;
import org.springframework.core.Ordered;
import io.micrometer.tracing.Tracer;

/** Servlet 请求关联 ID 自动配置。 */
@AutoConfiguration
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
public class TraceIdAutoConfiguration {

    /** 在 HTTP 观测过滤器建立 span 后注册兼容关联 ID 过滤器。 */
    @Bean
    public FilterRegistrationBean<TraceIdFilter> traceIdFilterRegistration(ObjectProvider<Tracer> tracerProvider) {
        FilterRegistrationBean<TraceIdFilter> registration = new FilterRegistrationBean<>();
        registration.setFilter(new TraceIdFilter(tracerProvider::getIfAvailable));
        registration.addUrlPatterns("/*");
        registration.setName("traceIdFilter");
        registration.setOrder(Ordered.HIGHEST_PRECEDENCE + 10);
        return registration;
    }
}
