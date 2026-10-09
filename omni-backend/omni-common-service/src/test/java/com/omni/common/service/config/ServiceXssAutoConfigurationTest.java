package com.omni.common.service.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.omni.common.core.security.XssConfigProvider;
import com.omni.common.security.xss.XssAutoConfiguration;
import com.omni.common.security.xss.XssFilter;
import com.omni.common.service.xss.XssSettingsResolver;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.WebApplicationContextRunner;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.data.redis.core.StringRedisTemplate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

/** Starter XSS 提供者与公共过滤器自动配置顺序测试。 */
class ServiceXssAutoConfigurationTest {

    private final WebApplicationContextRunner contextRunner = new WebApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(
                    XssAutoConfiguration.class, ServiceXssAutoConfiguration.class))
            .withBean(ServiceIdentityProperties.class, () -> {
                ServiceIdentityProperties properties = new ServiceIdentityProperties();
                properties.setName("omni-test");
                properties.setDisplayName("Test");
                properties.getXss().setEnabled(true);
                return properties;
            })
            .withBean(ObjectMapper.class, ObjectMapper::new)
            .withBean(StringRedisTemplate.class, () -> mock(StringRedisTemplate.class))
            .withBean(XssSettingsResolver.class, () -> mock(XssSettingsResolver.class))
            .withPropertyValues("omni.service.xss.enabled=true");

    /** Starter Provider 必须先创建，使公共 Filter 与 Jackson 模块都能注册。 */
    @Test
    void shouldCreateProviderBeforeCommonXssInfrastructure() {
        contextRunner.run(context -> {
            assertThat(context).hasSingleBean(XssConfigProvider.class);
            assertThat(context).hasBean("xssFilterRegistration");
            assertThat(context).hasBean("xssJacksonModule");
            assertThat(context).hasBean("xssJackson3Module");

            @SuppressWarnings("unchecked")
            FilterRegistrationBean<XssFilter> registration =
                    (FilterRegistrationBean<XssFilter>) context.getBean("xssFilterRegistration");
            assertThat(registration.getFilter()).isInstanceOf(XssFilter.class);
            assertThat(registration.isEnabled()).isTrue();
        });
    }
}
