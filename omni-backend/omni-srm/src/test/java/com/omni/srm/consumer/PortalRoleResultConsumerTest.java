package com.omni.srm.consumer;

import com.omni.srm.dto.PortalRoleResultEvent;
import com.omni.common.service.datascope.ServiceDataScopeContext;
import com.omni.common.service.identity.ServiceIdentityContext;
import com.omni.srm.service.PortalRoleResultService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Map;
import java.util.function.Consumer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.verify;

/**
 * 门户角色结果消息契约和租户上下文测试。
 */
@ExtendWith(MockitoExtension.class)
class PortalRoleResultConsumerTest {

    @Mock private PortalRoleResultService service;

    @AfterEach
    void tearDown() {
        ServiceDataScopeContext.clear();
        ServiceIdentityContext.clear();
    }

    @Test
    void shouldEstablishAndClearTenantContextForMqConsumer() {
        doAnswer(invocation -> {
            assertEquals(1L, ServiceIdentityContext.requireTenantId());
            assertEquals("TENANT", ServiceDataScopeContext.require().effectiveScope());
            return null;
        }).when(service).handle(any());
        PortalRoleResultConsumer consumerConfig = new PortalRoleResultConsumer(service);
        Consumer<Map<String, Object>> consumer = consumerConfig.portalRoleResultFunction();

        consumer.accept(successEnvelope(1L, 1L));

        ArgumentCaptor<PortalRoleResultEvent> captor = ArgumentCaptor.forClass(PortalRoleResultEvent.class);
        verify(service).handle(captor.capture());
        assertEquals("request-1", captor.getValue().getRequestId());
        assertThrows(RuntimeException.class, ServiceIdentityContext::requireTenantId);
    }

    @Test
    void shouldRejectTenantMismatch() {
        PortalRoleResultConsumer consumerConfig = new PortalRoleResultConsumer(service);
        Consumer<Map<String, Object>> consumer = consumerConfig.portalRoleResultFunction();

        assertThrows(IllegalArgumentException.class, () -> consumer.accept(successEnvelope(1L, 2L)));
    }

    private Map<String, Object> successEnvelope(Long envelopeTenant, Long payloadTenant) {
        Map<String, Object> payload = Map.of(
                "requestId", "request-1",
                "tenantId", payloadTenant,
                "supplierId", 10L,
                "userId", 20L,
                "roleCode", "SUPPLIER",
                "result", "SUCCESS",
                "errorCode", "");
        return Map.of(
                "eventId", "event-1",
                "eventType", "auth.portal-role.assigned.v1",
                "tenantId", envelopeTenant,
                "correlationId", "request-1",
                "payload", payload);
    }
}
