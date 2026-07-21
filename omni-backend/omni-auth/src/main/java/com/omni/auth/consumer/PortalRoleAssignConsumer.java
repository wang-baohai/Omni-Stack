package com.omni.auth.consumer;

import com.omni.auth.dto.PortalRoleAssignmentCommand;
import com.omni.auth.service.PortalRoleAssignmentService;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.Map;
import java.util.function.Consumer;

/**
 * 门户角色分配消费者（Auth 侧）。
 *
 * @author Omni-Stack Team
 */
@Configuration
@RequiredArgsConstructor
public class PortalRoleAssignConsumer {

    private static final String ASSIGN_REQUEST_EVENT = "srm.portal-role.assign-requested.v1";
    private final PortalRoleAssignmentService portalRoleAssignmentService;

    /**
     * 消费 SRM 门户角色分配请求。
     *
     * @return 消息消费函数
     */
    @Bean(name = "portalRoleAssignFunction")
    public Consumer<Map<String, Object>> portalRoleAssignFunction() {
        return message -> {
            if (!ASSIGN_REQUEST_EVENT.equals(stringValue(message.get("eventType")))) {
                return;
            }
            PortalRoleAssignmentCommand command = parse(message);
            portalRoleAssignmentService.assign(command);
        };
    }

    private PortalRoleAssignmentCommand parse(Map<String, Object> message) {
        Map<String, Object> payload = extractPayload(message);
        String requestId = coalesceMatching(
                stringValue(message.get("correlationId")),
                requiredString(payload, "requestId"), "requestId");
        Long tenantId = coalesceMatching(
                longValue(message.get("tenantId")),
                requiredLong(payload, "tenantId"), "tenantId");
        return PortalRoleAssignmentCommand.builder()
                .requestId(requestId)
                .tenantId(tenantId)
                .supplierId(requiredLong(payload, "supplierId"))
                .userId(requiredLong(payload, "userId"))
                .roleCode(requiredString(payload, "roleCode"))
                .build();
    }

    private Map<String, Object> extractPayload(Map<String, Object> message) {
        Object payload = message.get("payload");
        if (payload instanceof Map<?, ?> payloadMap) {
            @SuppressWarnings("unchecked")
            Map<String, Object> typedPayload = (Map<String, Object>) payloadMap;
            return typedPayload;
        }
        throw new IllegalArgumentException("门户角色分配请求缺少 payload");
    }

    private String requiredString(Map<String, Object> source, String key) {
        String value = stringValue(source.get(key));
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("门户角色分配请求缺少 " + key);
        }
        return value;
    }

    private Long requiredLong(Map<String, Object> source, String key) {
        Long value = longValue(source.get(key));
        if (value == null) {
            throw new IllegalArgumentException("门户角色分配请求缺少 " + key);
        }
        return value;
    }

    private <T> T coalesceMatching(T envelopeValue, T payloadValue, String field) {
        if (envelopeValue != null && !envelopeValue.equals(payloadValue)) {
            throw new IllegalArgumentException("门户角色分配请求 " + field + " 不一致");
        }
        return envelopeValue != null ? envelopeValue : payloadValue;
    }

    private String stringValue(Object value) {
        return value == null ? null : value.toString();
    }

    private Long longValue(Object value) {
        if (value instanceof Number number) {
            return number.longValue();
        }
        if (value == null) {
            return null;
        }
        try {
            return Long.valueOf(value.toString());
        } catch (NumberFormatException exception) {
            throw new IllegalArgumentException("门户角色分配请求包含非法数值", exception);
        }
    }
}
