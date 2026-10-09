package com.omni.common.service.config;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ServiceConfigurationValidatorTest {

    @Test
    void shouldAllowCompletelyDisabledStarterWithoutIdentityMetadata() {
        ServiceIdentityProperties properties = new ServiceIdentityProperties();

        assertThatCode(() -> new ServiceConfigurationValidator(properties).afterPropertiesSet())
                .doesNotThrowAnyException();
    }

    @Test
    void shouldFailWhenEnabledFeatureHasNoServiceIdentity() {
        ServiceIdentityProperties properties = new ServiceIdentityProperties();
        properties.getGatewayPreauth().setEnabled(true);

        assertThatThrownBy(() -> new ServiceConfigurationValidator(properties).afterPropertiesSet())
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("omni.service.name");
    }

    @Test
    void shouldRejectWeakInternalToken() {
        ServiceIdentityProperties properties = validProperties();
        properties.getInternalApi().setEnabled(true);
        properties.getInternalApi().setToken("changeme");

        assertThatThrownBy(() -> new ServiceConfigurationValidator(properties).afterPropertiesSet())
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("至少 32 位");
    }

    @Test
    void shouldAcceptStrongInternalToken() {
        ServiceIdentityProperties properties = validProperties();
        properties.getInternalApi().setEnabled(true);
        properties.getInternalApi().setToken("starter-test-token-0123456789abcdef");

        assertThatCode(() -> new ServiceConfigurationValidator(properties).afterPropertiesSet())
                .doesNotThrowAnyException();
    }

    private ServiceIdentityProperties validProperties() {
        ServiceIdentityProperties properties = new ServiceIdentityProperties();
        properties.setName("omni-test");
        properties.setDisplayName("Test");
        return properties;
    }
}
