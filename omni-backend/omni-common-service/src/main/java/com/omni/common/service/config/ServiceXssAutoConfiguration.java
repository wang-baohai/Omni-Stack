package com.omni.common.service.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.omni.common.core.security.XssConfigProvider;
import com.omni.common.security.xss.XssAutoConfiguration;
import com.omni.common.service.xss.CachedServiceXssConfigProvider;
import com.omni.common.service.xss.DefaultXssSettingsFallback;
import com.omni.common.service.xss.XssSettingsFallback;
import com.omni.common.service.xss.XssSettingsResolver;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.AutoConfigureBefore;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.data.redis.core.StringRedisTemplate;

/**
 * 业务服务 XSS 设置缓存、Auth 回源与安全基线自动配置。
 *
 * @author Omni-Stack Team
 */
@AutoConfiguration
@AutoConfigureBefore(XssAutoConfiguration.class)
@ConditionalOnClass({XssConfigProvider.class, StringRedisTemplate.class})
@ConditionalOnProperty(prefix = "omni.service.xss", name = "enabled", havingValue = "true")
public class ServiceXssAutoConfiguration {

    /**
     * 创建默认安全基线。
     *
     * @return XSS 安全基线
     */
    @Bean
    @ConditionalOnMissingBean
    public XssSettingsFallback xssSettingsFallback() {
        return new DefaultXssSettingsFallback();
    }

    /**
     * 创建共享 XSS 配置提供者；权威解析器缺失时启动失败。
     *
     * @param redisTemplate Redis 模板
     * @param objectMapper JSON 映射器
     * @param resolver Auth 权威解析器
     * @param fallback 安全基线
     * @param properties 服务属性
     * @return XSS 配置提供者
     */
    @Bean
    @ConditionalOnMissingBean(XssConfigProvider.class)
    public XssConfigProvider xssConfigProvider(StringRedisTemplate redisTemplate,
                                               ObjectMapper objectMapper,
                                               XssSettingsResolver resolver,
                                               XssSettingsFallback fallback,
                                               ServiceIdentityProperties properties) {
        return new CachedServiceXssConfigProvider(redisTemplate, objectMapper, resolver, fallback, properties);
    }
}
