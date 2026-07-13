package com.omni.common.operlog.sanitize;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.MatchResult;
import java.util.regex.Pattern;

/**
 * 操作日志敏感信息清洗器。
 * <p>在请求参数、实体快照和异常信息进入 MQ 前完成字段级与文本级脱敏，
 * 避免 CRM 联系方式、凭证和自由文本进入审计日志。</p>
 *
 * @author Omni-Stack Team
 */
public class OperLogSanitizer {

    private static final String REDACTED = "[REDACTED]";
    private static final Pattern MOBILE_PATTERN = Pattern.compile("(?<!\\d)(1[3-9]\\d{9})(?!\\d)");
    private static final Pattern EMAIL_PATTERN = Pattern.compile(
            "([A-Za-z0-9._%+-])([A-Za-z0-9._%+-]*)(@[A-Za-z0-9.-]+\\.[A-Za-z]{2,})");
    private static final Pattern CREDENTIAL_PATTERN = Pattern.compile(
            "(?i)(bearer\\s+|(?:access[_-]?token|refresh[_-]?token|password|secret)\\s*[=:]\\s*)[^\\s,;]+"
    );

    private static final Set<String> REDACTED_FIELDS = Set.of(
            "password", "passwd", "pwd", "credential", "authorization", "token",
            "accesstoken", "refreshtoken", "secret", "clientsecret", "idcard",
            "identityno", "address", "content", "remark", "remarks", "note", "notes",
            "description", "customername", "contactname", "leadname", "companyname",
            "fullname", "opportunityname", "subject", "reason"
    );
    private static final Set<String> PHONE_FIELDS = Set.of("phone", "mobile", "telephone", "tel");
    private static final Set<String> EMAIL_FIELDS = Set.of("email", "mail");

    private final ObjectMapper objectMapper;

    /**
     * 创建脱敏器。
     *
     * @param objectMapper Jackson 对象映射器
     */
    public OperLogSanitizer(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    /**
     * 清洗 JSON 字符串中的敏感字段。
     * <p>不是合法 JSON 时退回文本模式，仍会处理手机号、邮箱和凭证片段。</p>
     *
     * @param json          原始 JSON
     * @param excludeFields 注解额外指定的排除字段
     * @return 清洗后的 JSON 或文本
     */
    public String sanitizeJson(String json, String[] excludeFields) {
        if (json == null || json.isBlank()) {
            return json;
        }
        try {
            JsonNode root = objectMapper.readTree(json);
            sanitizeNode(root, normalizeFields(excludeFields));
            return objectMapper.writeValueAsString(root);
        } catch (JsonProcessingException ex) {
            return sanitizeText(json);
        }
    }

    /**
     * 清洗非结构化文本中的常见敏感信息。
     *
     * @param text 原始文本
     * @return 清洗后的文本
     */
    public String sanitizeText(String text) {
        if (text == null || text.isBlank()) {
            return text;
        }
        String sanitized = maskEmails(text);
        sanitized = MOBILE_PATTERN.matcher(sanitized).replaceAll(match -> maskPhone(match.group(1)));
        return CREDENTIAL_PATTERN.matcher(sanitized).replaceAll(match -> credentialPrefix(match) + REDACTED);
    }

    private void sanitizeNode(JsonNode node, Set<String> excludedFields) {
        if (node instanceof ObjectNode objectNode) {
            Iterator<Map.Entry<String, JsonNode>> fields = objectNode.properties().iterator();
            while (fields.hasNext()) {
                Map.Entry<String, JsonNode> field = fields.next();
                String normalizedName = normalize(field.getKey());
                JsonNode value = field.getValue();
                if (excludedFields.contains(normalizedName) || isRedactedField(normalizedName)) {
                    objectNode.put(field.getKey(), REDACTED);
                } else if (isPhoneField(normalizedName) && value.isTextual()) {
                    objectNode.put(field.getKey(), maskPhone(value.asText()));
                } else if (isEmailField(normalizedName) && value.isTextual()) {
                    objectNode.put(field.getKey(), maskEmail(value.asText()));
                } else {
                    sanitizeNode(value, excludedFields);
                }
            }
        } else if (node instanceof ArrayNode arrayNode) {
            arrayNode.forEach(child -> sanitizeNode(child, excludedFields));
        }
    }

    private boolean isRedactedField(String fieldName) {
        return REDACTED_FIELDS.stream().anyMatch(fieldName::contains);
    }

    private boolean isPhoneField(String fieldName) {
        return PHONE_FIELDS.stream().anyMatch(fieldName::contains);
    }

    private boolean isEmailField(String fieldName) {
        return EMAIL_FIELDS.stream().anyMatch(fieldName::contains);
    }

    private Set<String> normalizeFields(String[] fields) {
        Set<String> normalized = new HashSet<>();
        if (fields != null) {
            Arrays.stream(fields).map(this::normalize).forEach(normalized::add);
        }
        return normalized;
    }

    private String normalize(String value) {
        return value == null ? "" : value.replace("_", "")
                .replace("-", "")
                .toLowerCase(Locale.ROOT);
    }

    private String maskEmails(String text) {
        return EMAIL_PATTERN.matcher(text).replaceAll(match -> maskEmail(match.group()));
    }

    private String maskEmail(String email) {
        int atIndex = email.indexOf('@');
        if (atIndex <= 0) {
            return REDACTED;
        }
        String local = email.substring(0, atIndex);
        String domain = email.substring(atIndex);
        return local.charAt(0) + "***" + domain;
    }

    private String maskPhone(String phone) {
        if (phone == null || phone.length() < 7) {
            return REDACTED;
        }
        return phone.substring(0, 3) + "****" + phone.substring(phone.length() - 4);
    }

    private String credentialPrefix(MatchResult matcher) {
        String matched = matcher.group();
        int separator = Math.max(matched.lastIndexOf('='), matched.lastIndexOf(':'));
        if (separator >= 0) {
            return matched.substring(0, separator + 1);
        }
        return matched.regionMatches(true, 0, "bearer", 0, 6) ? "Bearer " : "";
    }
}
