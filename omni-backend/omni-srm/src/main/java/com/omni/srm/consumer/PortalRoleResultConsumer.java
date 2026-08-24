package com.omni.srm.consumer;

import com.omni.srm.security.SrmPortalScope;
import com.omni.srm.dto.PortalRoleResultEvent;
import com.omni.common.service.datascope.ServiceDataScopeContext;
import com.omni.common.service.identity.ServiceIdentityContext;
import com.omni.common.service.identity.ServiceRequestIdentity;
import com.omni.srm.service.PortalRoleResultService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.Map;
import java.util.function.Consumer;

/**
 * 门户角色分配结果消费者。
 *
 * @author Omni-Stack Team
 */
@Slf4j
@Configuration
@RequiredArgsConstructor
public class PortalRoleResultConsumer {

    private static final String ASSIGNED_EVENT = "auth.portal-role.assigned.v1";
    private static final String FAILED_EVENT = "auth.portal-role.assign-failed.v1";

    private final PortalRoleResultService portalRoleResultService;

    /**
     * 消费 Auth 侧发回的角色分配结果事件。
     *
     * @return 消息消费函数
     */
    @Bean(name = "portalRoleResultFunction")
    public Consumer<Map<String, Object>> portalRoleResultFunction() {
        return message -> {
            String eventType = stringValue(message.get("eventType"));
            if (!ASSIGNED_EVENT.equals(eventType) && !FAILED_EVENT.equals(eventType)) {
                return;
            }
            PortalRoleResultEvent event = parse(message, eventType);
            ServiceIdentityContext.set(new ServiceRequestIdentity(
                    event.getUserId(), event.getTenantId(), "portal-role-saga"));
            try {
                SrmPortalScope.run(() -> {
                    portalRoleResultService.handle(event);
                    return null;
                });
            } finally {
                ServiceDataScopeContext.clear();
                ServiceIdentityContext.clear();
            }
        };
    }

    private PortalRoleResultEvent parse(Map<String, Object> message, String eventType) {
        Map<String, Object> payload = extractPayload(message);
        PortalRoleResultEvent event = new PortalRoleResultEvent();
        event.setEventId(requiredString(message, "eventId"));
        event.setEventType(eventType);
        event.setRequestId(coalesceMatching(
                stringValue(message.get("correlationId")), stringValue(payload.get("requestId")), "requestId"));
        event.setTenantId(coalesceMatching(
                longValue(message.get("tenantId")), longValue(payload.get("tenantId")), "tenantId"));
        event.setSupplierId(requiredLong(payload, "supplierId"));
        event.setUserId(requiredLong(payload, "userId"));
        event.setRoleCode(requiredString(payload, "roleCode"));
        String expectedResult = ASSIGNED_EVENT.equals(eventType) ? "SUCCESS" : "FAILED";
        String payloadResult = stringValue(payload.get("result"));
        if (payloadResult != null && !expectedResult.equals(payloadResult)) {
            throw new IllegalArgumentException("门户角色结果事件类型与 result 不一致");
        }
        event.setResult(expectedResult);
        event.setErrorCode(stringValue(payload.get("errorCode")) != null
                ? stringValue(payload.get("errorCode")) : stringValue(message.get("errorCode")));
        return event;
    }

    private Map<String, Object> extractPayload(Map<String, Object> message) {
        Object payload = message.get("payload");
        if (payload instanceof Map<?, ?> payloadMap) {
            @SuppressWarnings("unchecked")
            Map<String, Object> typedPayload = (Map<String, Object>) payloadMap;
            return typedPayload;
        }
        throw new IllegalArgumentException("门户角色结果缺少 payload");
    }

    private String requiredString(Map<String, Object> source, String key) {
        String value = stringValue(source.get(key));
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("门户角色结果缺少 " + key);
        }
        return value;
    }

    private Long requiredLong(Map<String, Object> source, String key) {
        Long value = longValue(source.get(key));
        if (value == null) {
            throw new IllegalArgumentException("门户角色结果缺少 " + key);
        }
        return value;
    }

    private <T> T coalesceMatching(T envelopeValue, T payloadValue, String field) {
        if (envelopeValue == null && payloadValue == null) {
            throw new IllegalArgumentException("门户角色结果缺少 " + field);
        }
        if (envelopeValue != null && payloadValue != null && !envelopeValue.equals(payloadValue)) {
            throw new IllegalArgumentException("门户角色结果 " + field + " 不一致");
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
            log.warn("门户角色结果包含非法数值字段");
            return null;
        }
    }
}
