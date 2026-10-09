package com.omni.common.service.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.omni.common.service.identity.GatewayPreAuthenticationFilter;
import com.omni.common.service.identity.ServiceIdentityFilter;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.WebApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import static org.assertj.core.api.Assertions.assertThat;

class ServiceCoreAutoConfigurationTest {

    private final WebApplicationContextRunner contextRunner = new WebApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(ServiceCoreAutoConfiguration.class))
            .withUserConfiguration(JacksonConfiguration.class);

    @Test
    void shouldStayDormantWhenAllFeaturesAreDisabled() {
        contextRunner.run(context -> {
            assertThat(context).hasNotFailed();
            assertThat(context).doesNotHaveBean(GatewayPreAuthenticationFilter.class);
            assertThat(context).doesNotHaveBean(ServiceIdentityFilter.class);
        });
    }

    @Test
    void shouldCreateIdentityFiltersOnlyWithCompleteMetadata() {
        contextRunner.withPropertyValues(
                        "omni.service.name=omni-test",
                        "omni.service.display-name=Test",
                        "omni.service.gateway-preauth.enabled=true")
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    assertThat(context).hasSingleBean(GatewayPreAuthenticationFilter.class);
                    assertThat(context).hasSingleBean(ServiceIdentityFilter.class);
                    assertThat(context).hasBean("gatewayPreAuthenticationFilterRegistration");
                    assertThat(context).hasBean("serviceIdentityFilterRegistration");
                });
    }

    @Test
    void shouldFailStartupWhenEnabledFeatureHasNoServiceName() {
        contextRunner.withPropertyValues(
                        "omni.service.display-name=Test",
                        "omni.service.gateway-preauth.enabled=true")
                .run(context -> assertThat(context).hasFailed());
    }

    @Configuration(proxyBeanMethods = false)
    static class JacksonConfiguration {

        @Bean
        ObjectMapper objectMapper() {
            return new ObjectMapper();
        }
    }
}
