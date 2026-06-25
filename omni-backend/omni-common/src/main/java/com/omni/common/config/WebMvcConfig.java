package com.omni.common.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * Web MVC 配置类，处理跨域资源共享（CORS）等 Web 层配置。
 * <p>实现 {@link WebMvcConfigurer} 接口，全局放开 CORS 策略。
 * 开发环境允许所有来源的跨域请求；生产环境可通过 Nginx 或 Gateway 层进一步限制。</p>
 *
 * @see CorsRegistry
 */
@Configuration
public class WebMvcConfig implements WebMvcConfigurer {

    /**
     * 配置全局 CORS 跨域策略。
     * <p>
     * 允许所有来源、所有请求方法和所有请求头，
     * 支持携带凭证（Cookie）的跨域请求，预检请求缓存时间为 3600 秒。
     * </p>
     *
     * @param registry CORS 注册器
     */
    @Override
    public void addCorsMappings(CorsRegistry registry) {
        registry.addMapping("/**")
                .allowedOriginPatterns("*")
                .allowedMethods("GET", "POST", "PUT", "DELETE", "OPTIONS")
                .allowedHeaders("*")
                .allowCredentials(true)
                .maxAge(3600);
    }
}
