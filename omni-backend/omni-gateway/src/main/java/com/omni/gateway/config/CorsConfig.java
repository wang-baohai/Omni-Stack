package com.omni.gateway.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.reactive.CorsWebFilter;
import org.springframework.web.cors.reactive.UrlBasedCorsConfigurationSource;

/**
 * 网关 CORS 跨域配置（响应式 WebFlux 技术栈）。
 * <p>
 * 使用 {@link CorsWebFilter} 替代传统的 MVC 跨域配置，
 * 适配 Spring Cloud Gateway 的响应式编程模型。
 * 该过滤器必须在 {@link com.omni.gateway.filter.AuthFilter} 之前执行，
 * 以确保预检请求（OPTIONS）不被认证过滤器拦截。</p>
 *
 * @see com.omni.gateway.filter.AuthFilter
 */
@Configuration
public class CorsConfig {

    /**
     * 允许的跨域来源列表，通过配置属性 {@code omni.cors.allowed-origins} 控制。
     * <p>开发环境默认允许所有来源（{@code *}），生产环境应限制为前端域名。</p>
     */
    @Value("${omni.cors.allowed-origins:*}")
    private String allowedOrigins;

    /**
     * 创建并配置 CORS 跨域过滤器。
     * <p>
     * 允许所有来源、所有方法和所有请求头，支持携带凭证，
     * 预检请求缓存时间为 3600 秒。
     * </p>
     *
     * @return 配置完成的 CORS 过滤器
     */
    @Bean
    public CorsWebFilter corsWebFilter() {
        CorsConfiguration config = new CorsConfiguration();
        // 根据配置属性设置允许的来源（支持逗号分隔多个域名）
        if ("*".equals(allowedOrigins)) {
            config.addAllowedOriginPattern("*");
        } else {
            for (String origin : allowedOrigins.split(",")) {
                config.addAllowedOriginPattern(origin.trim());
            }
        }
        // 允许所有 HTTP 方法
        config.addAllowedMethod("*");
        // 允许所有请求头
        config.addAllowedHeader("*");
        // 允许携带凭证（Cookie 等）
        config.setAllowCredentials(true);
        // 预检请求缓存时间（秒）
        config.setMaxAge(3600L);

        // 注册跨域配置，对所有路径生效
        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", config);
        return new CorsWebFilter(source);
    }
}
