package com.omni.asset.workflow;

import com.omni.asset.client.WorkflowInternalClient;
import com.omni.asset.dto.WorkflowContracts;
import com.omni.asset.security.AssetTenantContext;
import com.omni.asset.service.AssetOperationWorkflowStateService;
import com.omni.common.core.result.BusinessException;
import com.omni.common.core.result.R;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** 资产 Workflow 启动结果分类与幂等恢复测试。 */
@ExtendWith(MockitoExtension.class)
class AssetWorkflowCoordinatorTest {

    @Mock private WorkflowInternalClient workflowInternalClient;
    @Mock private AssetOperationWorkflowStateService workflowStateService;

    private AssetWorkflowCoordinator coordinator;

    /** 初始化协调器和租户身份。 */
    @BeforeEach
    void setUp() {
        coordinator = new AssetWorkflowCoordinator(workflowInternalClient, workflowStateService);
        AssetTenantContext.set(new AssetTenantContext.RequestIdentity(7L, 1L, "admin"));
    }

    /** 清理租户身份。 */
    @AfterEach
    void clearContext() {
        AssetTenantContext.clear();
    }

    /** 409 可能表示同键请求仍在处理，必须保留 PENDING。 */
    @Test
    void shouldKeepPendingForConflictResponse() {
        AssetWorkflowCommand command = command();
        when(workflowInternalClient.start(any(), any())).thenReturn(R.fail(409, "processing"));

        assertThatThrownBy(() -> coordinator.start(command))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("待确认");
        verify(workflowStateService, never()).markFailed(command);
        verify(workflowStateService, never()).markStarted(any(), any());
    }

    /** Workflow 明确返回模型不可启动时，远端尚未创建实例，可以进入失败态。 */
    @Test
    void shouldMarkFailedWhenWorkflowConfirmsModelNotStartable() {
        AssetWorkflowCommand command = command();
        when(workflowInternalClient.start(any(), any())).thenReturn(R.fail(404, "not published"));

        assertThatThrownBy(() -> coordinator.start(command))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("可重试或取消");
        verify(workflowStateService).markFailed(command);
        verify(workflowStateService, never()).markStarted(any(), any());
    }

    /** 明确失败但本地状态落库失败时仍保持结果待确认，禁止错误开放取消。 */
    @Test
    void shouldKeepPendingWhenExplicitFailureCannotBePersisted() {
        AssetWorkflowCommand command = command();
        when(workflowInternalClient.start(any(), any())).thenReturn(R.fail(404, "not published"));
        org.mockito.Mockito.doThrow(new IllegalStateException("db unavailable"))
                .when(workflowStateService).markFailed(command);

        assertThatThrownBy(() -> coordinator.start(command))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("本地状态待确认");
        verify(workflowStateService, never()).markStarted(any(), any());
    }

    /** 调用异常属于未知结果，必须保留 PENDING 并复用原键重试。 */
    @Test
    void shouldKeepPendingWhenRemoteOutcomeIsUnknown() {
        AssetWorkflowCommand command = command();
        when(workflowInternalClient.start(any(), any()))
                .thenThrow(new IllegalStateException("connection reset"));

        assertThatThrownBy(() -> coordinator.start(command))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("待确认");
        verify(workflowStateService, never()).markFailed(any());
        verify(workflowStateService, never()).markStarted(any(), any());
    }

    /** 远端已经成功但本地确认失败时不得降级为 START_FAILED。 */
    @Test
    void shouldKeepPendingWhenLocalStartConfirmationFails() {
        AssetWorkflowCommand command = command();
        when(workflowInternalClient.start(any(), any())).thenReturn(successResponse());
        org.mockito.Mockito.doThrow(new IllegalStateException("db unavailable"))
                .when(workflowStateService).markStarted(command, "process-1");

        assertThatThrownBy(() -> coordinator.start(command))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("本地状态待确认");
        verify(workflowStateService, never()).markFailed(any());
    }

    /** 成功响应必须使用原请求意图确认本地启动状态。 */
    @Test
    void shouldMarkStartedForMatchingSuccessResponse() {
        AssetWorkflowCommand command = command();
        when(workflowInternalClient.start(any(), any())).thenReturn(successResponse());

        coordinator.start(command);

        verify(workflowStateService).markStarted(command, "process-1");
        verify(workflowStateService, never()).markFailed(any());
    }

    private AssetWorkflowCommand command() {
        return new AssetWorkflowCommand(
                "TRANSFER", 10L, 1L, "request-1",
                AssetWorkflowCoordinator.TRANSFER_BUSINESS_TYPE, "10", 42L,
                7L, "admin", "资产调拨 AT-1-10", Map.of("transferId", 10L));
    }

    private R<WorkflowContracts.StartResponse> successResponse() {
        WorkflowContracts.StartResponse response = new WorkflowContracts.StartResponse();
        response.setRequestId("request-1");
        response.setBusinessType(AssetWorkflowCoordinator.TRANSFER_BUSINESS_TYPE);
        response.setBusinessKey("10");
        response.setProcessInstanceId("process-1");
        return R.ok(response);
    }
}
