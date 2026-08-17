package com.omni.common.security.xss;

import com.omni.common.core.security.XssConfigProvider;
import com.omni.common.core.security.XssSettings;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.http.converter.json.JacksonJsonHttpMessageConverter;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Servlet HTTP JSON 请求体的 XSS 集成测试。 */
class XssHttpJsonIntegrationTest {

    /** 恶意 JSON 必须经过真实过滤器和 Jackson 3 Converter 后再进入控制器。 */
    @Test
    void shouldSanitizeJsonBodyThroughMvcPipeline() throws Exception {
        XssConfigProvider provider = tenantId -> XssSettings.builder()
                .enabled(true)
                .rules(baselineRules())
                .build();
        tools.jackson.databind.json.JsonMapper mapper =
                tools.jackson.databind.json.JsonMapper.builder()
                        .addModule(new XssAutoConfiguration().xssJackson3Module())
                        .build();
        MockMvc mockMvc = MockMvcBuilders.standaloneSetup(new EchoController())
                .setMessageConverters(new JacksonJsonHttpMessageConverter(mapper))
                .addFilter(new XssFilter(provider), "/*")
                .build();

        mockMvc.perform(post("/xss-test/echo")
                        .header("X-Tenant-Id", "1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"value\":\"safe<script>alert(1)</script>"
                                + "<img onerror=alert(2) src=javascript:demo>\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.value", containsString("safe")))
                .andExpect(jsonPath("$.value", not(containsString("script"))))
                .andExpect(jsonPath("$.value", not(containsString("onerror"))))
                .andExpect(jsonPath("$.value", not(containsString("javascript:"))));
    }

    private List<XssSettings.XssRule> baselineRules() {
        return List.of(
                XssSettings.XssRule.builder().id(1L).ruleType("HTML_TAG")
                        .pattern("script|iframe|object|embed|style").build(),
                XssSettings.XssRule.builder().id(2L).ruleType("EVENT_HANDLER")
                        .pattern("on[a-z]+").build(),
                XssSettings.XssRule.builder().id(3L).ruleType("DANGEROUS_PROTOCOL")
                        .pattern("javascript:|vbscript:|data:text/html").build());
    }

    /** 回显净化后请求体的最小控制器。 */
    @RestController
    static class EchoController {

        /** 返回实际绑定后的请求对象。 */
        @PostMapping("/xss-test/echo")
        Payload echo(@RequestBody Payload payload) {
            return payload;
        }
    }

    /** 测试请求体。 */
    record Payload(String value) {
    }
}
