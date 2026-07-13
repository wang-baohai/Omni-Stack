package com.omni.common.operlog.sanitize;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 操作日志脱敏器测试。
 *
 * @author Omni-Stack Team
 */
class OperLogSanitizerTest {

    private final ObjectMapper objectMapper = new ObjectMapper();
    private OperLogSanitizer sanitizer;

    @BeforeEach
    void setUp() {
        sanitizer = new OperLogSanitizer(objectMapper);
    }

    @Test
    void shouldSanitizeNestedCrmPersonalInformation() throws Exception {
        String source = """
                {"customerName":"张三公司","contact":{"mobile":"13812345678",\
                "email":"alice@example.com","remark":"重点客户"},"amount":1000}
                """;

        JsonNode result = objectMapper.readTree(sanitizer.sanitizeJson(source, new String[0]));

        assertThat(result.path("customerName").asText()).isEqualTo("[REDACTED]");
        assertThat(result.path("contact").path("mobile").asText()).isEqualTo("138****5678");
        assertThat(result.path("contact").path("email").asText()).isEqualTo("a***@example.com");
        assertThat(result.path("contact").path("remark").asText()).isEqualTo("[REDACTED]");
        assertThat(result.path("amount").asInt()).isEqualTo(1000);
    }

    @Test
    void shouldApplyAnnotationExcludedFields() throws Exception {
        JsonNode result = objectMapper.readTree(
                sanitizer.sanitizeJson("{\"customCode\":\"secret-value\"}", new String[]{"custom_code"}));

        assertThat(result.path("customCode").asText()).isEqualTo("[REDACTED]");
    }

    @Test
    void shouldRedactCrmNamesSubjectsAndReasons() throws Exception {
        String source = """
                {"fullName":"张三","opportunityName":"张三续约",\
                "subject":"致电张三","lossReason":"客户个人原因"}
                """;

        JsonNode result = objectMapper.readTree(sanitizer.sanitizeJson(source, new String[0]));

        assertThat(result.path("fullName").asText()).isEqualTo("[REDACTED]");
        assertThat(result.path("opportunityName").asText()).isEqualTo("[REDACTED]");
        assertThat(result.path("subject").asText()).isEqualTo("[REDACTED]");
        assertThat(result.path("lossReason").asText()).isEqualTo("[REDACTED]");
    }

    @Test
    void shouldSanitizeSensitiveFragmentsInPlainText() {
        String result = sanitizer.sanitizeText(
                "mobile=13812345678 email=alice@example.com bearer very-secret-token");

        assertThat(result).doesNotContain("13812345678", "alice@example.com", "very-secret-token")
                .contains("138****5678", "a***@example.com", "Bearer [REDACTED]");
    }
}
