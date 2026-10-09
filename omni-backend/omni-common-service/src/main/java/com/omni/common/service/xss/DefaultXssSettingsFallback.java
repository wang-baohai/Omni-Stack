package com.omni.common.service.xss;

import com.omni.common.core.security.XssSettings;

import java.util.List;

/**
 * 默认启用三类危险输入规则的 XSS 安全基线。
 *
 * @author Omni-Stack Team
 */
public class DefaultXssSettingsFallback implements XssSettingsFallback {

    /** {@inheritDoc} */
    @Override
    public XssSettings get() {
        return XssSettings.builder().enabled(true).rules(List.of(
                rule(-1L, "HTML_TAG", "script|iframe|object|embed|style"),
                rule(-2L, "EVENT_HANDLER", "on[a-zA-Z]+"),
                rule(-3L, "DANGEROUS_PROTOCOL", "javascript:|vbscript:|data:text/html")
        )).build();
    }

    private XssSettings.XssRule rule(Long id, String type, String pattern) {
        return XssSettings.XssRule.builder().id(id).ruleType(type).pattern(pattern).build();
    }
}
