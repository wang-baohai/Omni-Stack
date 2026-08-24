package com.omni.procurement.workflow;

import com.omni.common.core.result.BusinessException;
import com.omni.common.core.result.R;
import com.omni.procurement.client.WorkflowInternalClient;
import com.omni.procurement.dto.WorkflowContracts;
import com.omni.common.service.identity.ServiceIdentityContext;
import com.omni.common.service.identity.ServiceRequestIdentity;
import com.omni.procurement.service.RequisitionWorkflowStateService;
import feign.FeignException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** 请购 Workflow 启动协调器测试。 */
@ExtendWith(MockitoExtension.class)
class RequisitionWorkflowCoordinatorTest {

    @Mock private WorkflowInternalClient workflowInternalClient;
    @Mock private RequisitionWorkflowStateService workflowStateService;

    /** 清理租户上下文。 */
    @AfterEach
    void clearContext() {
        ServiceIdentityContext.clear();
    }

    /** 成功启动必须使用已持久化幂等键并标记 STARTED。 */
    @Test
    void shouldStartWithPersistedIdempotencySnapshot() {
        ServiceIdentityContext.set(new ServiceRequestIdentity(7L, 41L, "buyer"));
        RequisitionWorkflowCommand command = command();
        WorkflowContracts.StartResponse data = response(command, "pi-900");
        when(workflowInternalClient.start(any(), any())).thenReturn(R.ok(data));
        RequisitionWorkflowCoordinator coordinator = new RequisitionWorkflowCoordinator(
                workflowInternalClient, workflowStateService);

        coordinator.start(command);

        ArgumentCaptor<WorkflowContracts.StartRequest> captor =
                ArgumentCaptor.forClass(WorkflowContracts.StartRequest.class);
        verify(workflowInternalClient).start(org.mockito.ArgumentMatchers.eq(41L), captor.capture());
        WorkflowContracts.StartRequest request = captor.getValue();
        assertThat(request.getRequestId()).isEqualTo("req-fixed");
        assertThat(request.getBusinessKey()).isEqualTo("100:3");
        assertThat(request.getModelVersionId()).isEqualTo(88L);
        assertThat(request.getVariables())
                .containsEntry("approvalAttempt", 3)
                .containsEntry("totalAmount", "120000.0000");
        verify(workflowStateService).markStarted(command, "pi-900");
        verify(workflowStateService, never()).markFailed(command);
    }

    /** 下游失败必须标记 FAILED 并返回 503，供同一快照重试。 */
    @Test
    void shouldMarkFailedWhenWorkflowCallFails() {
        ServiceIdentityContext.set(new ServiceRequestIdentity(7L, 41L, "buyer"));
        RequisitionWorkflowCommand command = command();
        when(workflowInternalClient.start(any(), any())).thenThrow(FeignException.class);
        RequisitionWorkflowCoordinator coordinator = new RequisitionWorkflowCoordinator(
                workflowInternalClient, workflowStateService);

        assertThatThrownBy(() -> coordinator.start(command))
                .isInstanceOf(BusinessException.class)
                .extracting("code").isEqualTo(503);
        verify(workflowStateService).markFailed(command);
        verify(workflowStateService, never()).markStarted(any(), any());
    }

    /** 响应中的业务键不匹配时不能确认启动。 */
    @Test
    void shouldRejectMismatchedWorkflowResponse() {
        ServiceIdentityContext.set(new ServiceRequestIdentity(7L, 41L, "buyer"));
        RequisitionWorkflowCommand command = command();
        WorkflowContracts.StartResponse data = response(command, "pi-900");
        data.setBusinessKey("100:2");
        when(workflowInternalClient.start(any(), any())).thenReturn(R.ok(data));
        RequisitionWorkflowCoordinator coordinator = new RequisitionWorkflowCoordinator(
                workflowInternalClient, workflowStateService);

        assertThatThrownBy(() -> coordinator.start(command))
                .isInstanceOf(BusinessException.class)
                .extracting("code").isEqualTo(503);
        verify(workflowStateService).markFailed(command);
        verify(workflowStateService, never()).markStarted(any(), any());
    }

    private RequisitionWorkflowCommand command() {
        return new RequisitionWorkflowCommand(100L, 41L, "PR-41-100", "电脑采购", 7L, 12L,
                "IT", new BigDecimal("120000.0000"), "CNY", 3, "req-fixed", "100:3", 88L);
    }

    private WorkflowContracts.StartResponse response(RequisitionWorkflowCommand command,
                                                     String processInstanceId) {
        WorkflowContracts.StartResponse response = new WorkflowContracts.StartResponse();
        response.setRequestId(command.requestId());
        response.setBusinessType(RequisitionWorkflowCoordinator.BUSINESS_TYPE);
        response.setBusinessKey(command.businessKey());
        response.setProcessInstanceId(processInstanceId);
        return response;
    }
}
