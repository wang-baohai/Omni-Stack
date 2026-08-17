package com.omni.common.security.xss;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.module.SimpleModule;
import com.omni.common.core.security.XssSettings;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Jackson 2/3 JSON 请求体 XSS 净化一致性测试。
 */
class XssJsonDeserializationTest {

    /** 清理请求级规则，避免测试线程复用造成污染。 */
    @AfterEach
    void clearRules() {
        XssRuleHolder.clear();
    }

    /** 验证 Jackson 2 会清除多标签、多协议和事件处理器。 */
    @Test
    void shouldSanitizeJackson2StringFields() throws Exception {
        SimpleModule module = new SimpleModule("XssModuleTest");
        module.addDeserializer(String.class, new XssStringDeserializer(
                new com.fasterxml.jackson.databind.deser.std.StringDeserializer()));
        ObjectMapper mapper = new ObjectMapper().registerModule(module);
        XssRuleHolder.set(baselineRules());

        Payload payload = mapper.readValue(maliciousJson(), Payload.class);

        assertSanitized(payload.value());
    }

    /** 验证 Spring Boot 4 默认的 Jackson 3 会执行同等净化。 */
    @Test
    void shouldSanitizeJackson3StringFields() {
        tools.jackson.databind.module.SimpleModule module =
                new tools.jackson.databind.module.SimpleModule("XssModule3Test");
        module.addDeserializer(String.class, new XssStringDeserializer3());
        tools.jackson.databind.json.JsonMapper mapper = tools.jackson.databind.json.JsonMapper.builder()
                .addModule(module)
                .build();
        XssRuleHolder.set(baselineRules());

        Payload payload = mapper.readValue(maliciousJson(), Payload.class);

        assertSanitized(payload.value());
    }

    private static List<XssSettings.XssRule> baselineRules() {
        return List.of(
                XssSettings.XssRule.builder().id(1L).ruleType("HTML_TAG")
                        .pattern("script|iframe|object|embed|style").build(),
                XssSettings.XssRule.builder().id(2L).ruleType("EVENT_HANDLER")
                        .pattern("on[a-z]+").build(),
                XssSettings.XssRule.builder().id(3L).ruleType("DANGEROUS_PROTOCOL")
                        .pattern("javascript:|vbscript:|data:text/html").build());
    }

    private static String maliciousJson() {
        return "{\"value\":\"safe<script>alert(1)</script>"
                + "<img onerror=alert(2) src=javascript:demo>"
                + "<iframe>bad</iframe>\"}";
    }

    private static void assertSanitized(String value) {
        assertThat(value)
                .contains("safe")
                .doesNotContainIgnoringCase("script")
                .doesNotContainIgnoringCase("iframe")
                .doesNotContainIgnoringCase("onerror")
                .doesNotContainIgnoringCase("javascript:");
    }

    private record Payload(String value) {
    }
}
