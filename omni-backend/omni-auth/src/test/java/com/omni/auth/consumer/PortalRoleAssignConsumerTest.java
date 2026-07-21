package com.omni.auth.consumer;

import com.omni.auth.dto.PortalRoleAssignmentCommand;
import com.omni.auth.service.PortalRoleAssignmentService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Map;
import java.util.function.Consumer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.verify;

/**
 * 门户角色分配消息契约测试。
 */
@ExtendWith(MockitoExtension.class)
class PortalRoleAssignConsumerTest {

    @Mock private PortalRoleAssignmentService service;

    @Test
    void shouldParseUnifiedEnvelope() {
        PortalRoleAssignConsumer consumerConfig = new PortalRoleAssignConsumer(service);
        Consumer<Map<String, Object>> consumer = consumerConfig.portalRoleAssignFunction();
        Map<String, Object> payload = Map.of(
                "requestId", "request-1",
                "tenantId", 1L,
                "supplierId", 10L,
                "userId", 20L,
                "roleCode", "SUPPLIER");

        consumer.accept(Map.of(
                "eventType", "srm.portal-role.assign-requested.v1",
                "tenantId", 1L,
                "correlationId", "request-1",
                "payload", payload));

        ArgumentCaptor<PortalRoleAssignmentCommand> captor =
                ArgumentCaptor.forClass(PortalRoleAssignmentCommand.class);
        verify(service).assign(captor.capture());
        assertEquals(1L, captor.getValue().getTenantId());
        assertEquals(10L, captor.getValue().getSupplierId());
        assertEquals(20L, captor.getValue().getUserId());
    }

    @Test
    void shouldRejectTenantMismatchBetweenEnvelopeAndPayload() {
        PortalRoleAssignConsumer consumerConfig = new PortalRoleAssignConsumer(service);
        Consumer<Map<String, Object>> consumer = consumerConfig.portalRoleAssignFunction();
        Map<String, Object> payload = Map.of(
                "requestId", "request-1",
                "tenantId", 2L,
                "supplierId", 10L,
                "userId", 20L,
                "roleCode", "SUPPLIER");

        assertThrows(IllegalArgumentException.class, () -> consumer.accept(Map.of(
                "eventType", "srm.portal-role.assign-requested.v1",
                "tenantId", 1L,
                "correlationId", "request-1",
                "payload", payload)));
    }
}
