package com.omni.common.service.xss;

import com.omni.common.core.security.XssSettings;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class DefaultXssSettingsFallbackTest {

    @Test
    void shouldAlwaysEnableLocalSecurityBaseline() {
        XssSettings settings = new DefaultXssSettingsFallback().get();

        assertThat(settings.isEnabled()).isTrue();
        assertThat(settings.getRules())
                .extracting(XssSettings.XssRule::getRuleType)
                .containsExactly("HTML_TAG", "EVENT_HANDLER", "DANGEROUS_PROTOCOL");
    }
}
