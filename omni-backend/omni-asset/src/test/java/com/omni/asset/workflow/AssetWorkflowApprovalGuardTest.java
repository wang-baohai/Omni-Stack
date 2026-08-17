package com.omni.asset.workflow;

import com.omni.asset.client.WorkflowInternalClient;
import com.omni.asset.dto.WorkflowContracts;
import com.omni.common.core.result.BusinessException;
import com.omni.common.core.result.R;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** Asset 审批任务资格完整业务边界测试。 */
class AssetWorkflowApprovalGuardTest {

    /** 资格校验请求必须完整传递租户、任务、用户和业务关联标识。 */
    @Test
    void shouldValidateCompleteAssignmentIntent() {
        WorkflowInternalClient client = mock(WorkflowInternalClient.class);
        WorkflowContracts.AssignmentResponse response = assignment(true, "process-1");
        when(client.validateAssignment(
                org.mockito.ArgumentMatchers.eq(31L),
                org.mockito.ArgumentMatchers.any(WorkflowContracts.AssignmentRequest.class)))
                .thenReturn(R.ok(response));
        AssetWorkflowApprovalGuard guard = new AssetWorkflowApprovalGuard(client);

        guard.requireAssigned(new AssetWorkflowApprovalGuard.AssignmentIntent(
                31L, 7L, "task-1", AssetWorkflowCoordinator.TRANSFER_BUSINESS_TYPE,
                "100", "process-1"));

        ArgumentCaptor<WorkflowContracts.AssignmentRequest> captor =
                ArgumentCaptor.forClass(WorkflowContracts.AssignmentRequest.class);
        verify(client).validateAssignment(org.mockito.ArgumentMatchers.eq(31L), captor.capture());
        WorkflowContracts.AssignmentRequest request = captor.getValue();
        assertThat(request.getTenantId()).isEqualTo(31L);
        assertThat(request.getTaskId()).isEqualTo("task-1");
        assertThat(request.getUserId()).isEqualTo(7L);
        assertThat(request.getBusinessType())
                .isEqualTo(AssetWorkflowCoordinator.TRANSFER_BUSINESS_TYPE);
        assertThat(request.getBusinessKey()).isEqualTo("100");
    }

    /** Workflow 明确判定任务未分配时必须失败关闭。 */
    @Test
    void shouldRejectInvalidTaskAssignment() {
        WorkflowInternalClient client = mock(WorkflowInternalClient.class);
        when(client.validateAssignment(
                org.mockito.ArgumentMatchers.eq(31L),
                org.mockito.ArgumentMatchers.any(WorkflowContracts.AssignmentRequest.class)))
                .thenReturn(R.ok(assignment(false, "process-1")));

        assertThatThrownBy(() -> new AssetWorkflowApprovalGuard(client).requireAssigned(
                new AssetWorkflowApprovalGuard.AssignmentIntent(
                        31L, 7L, "task-1",
                        AssetWorkflowCoordinator.DISPOSAL_BUSINESS_TYPE,
                        "200", "process-1")))
                .isInstanceOf(BusinessException.class)
                .extracting("code").isEqualTo(403);
    }

    /** Workflow 返回的流程实例必须与 Asset 本地审批快照完全一致。 */
    @Test
    void shouldRejectMismatchedProcessInstance() {
        WorkflowInternalClient client = mock(WorkflowInternalClient.class);
        when(client.validateAssignment(
                org.mockito.ArgumentMatchers.eq(31L),
                org.mockito.ArgumentMatchers.any(WorkflowContracts.AssignmentRequest.class)))
                .thenReturn(R.ok(assignment(true, "other-process")));

        assertThatThrownBy(() -> new AssetWorkflowApprovalGuard(client).requireAssigned(
                new AssetWorkflowApprovalGuard.AssignmentIntent(
                        31L, 7L, "task-1",
                        AssetWorkflowCoordinator.TRANSFER_BUSINESS_TYPE,
                        "100", "process-1")))
                .isInstanceOf(BusinessException.class)
                .extracting("code").isEqualTo(403);
    }

    private WorkflowContracts.AssignmentResponse assignment(
            boolean valid, String processInstanceId) {
        WorkflowContracts.AssignmentResponse response =
                new WorkflowContracts.AssignmentResponse();
        response.setValid(valid);
        response.setProcessInstanceId(processInstanceId);
        response.setAssignmentType(valid ? "ASSIGNEE" : "NONE");
        return response;
    }
}
