package com.omni.asset.workflow;

import com.omni.asset.client.WorkflowInternalClient;
import com.omni.asset.dto.WorkflowContracts;
import com.omni.common.core.result.BusinessException;
import com.omni.common.core.result.R;
import feign.FeignException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * Asset 审批视图 Workflow 任务分配校验器。
 *
 * @author Omni-Stack Team
 */
@Component
@RequiredArgsConstructor
public class AssetWorkflowApprovalGuard {

    private final WorkflowInternalClient workflowInternalClient;

    /**
     * 校验任务属于当前用户、租户和业务申请。
     *
     * @param intent 任务、用户和业务关联意图
     */
    public void requireAssigned(AssignmentIntent intent) {
        WorkflowContracts.AssignmentRequest request = new WorkflowContracts.AssignmentRequest();
        request.setTenantId(intent.tenantId());
        request.setTaskId(intent.taskId());
        request.setUserId(intent.userId());
        request.setBusinessType(intent.businessType());
        request.setBusinessKey(intent.businessKey());
        try {
            R<WorkflowContracts.AssignmentResponse> response =
                    workflowInternalClient.validateAssignment(intent.tenantId(), request);
            WorkflowContracts.AssignmentResponse data =
                    response == null ? null : response.getData();
            if (response == null || response.getCode() != 200 || data == null) {
                throw new BusinessException(503, "Workflow 任务资格校验服务暂时不可用");
            }
            if (!data.isValid() || !intent.processInstanceId().equals(data.getProcessInstanceId())) {
                throw new BusinessException(403, "当前任务未分配给当前用户或不属于该资产申请");
            }
        } catch (FeignException.Forbidden exception) {
            throw new BusinessException(403, "Workflow 拒绝任务资格校验");
        } catch (FeignException exception) {
            throw new BusinessException(503, "Workflow 任务资格校验服务暂时不可用");
        }
    }

    /**
     * 审批任务资格校验意图。
     *
     * @param tenantId 租户 ID
     * @param userId 用户 ID
     * @param taskId 任务 ID
     * @param businessType 业务类型
     * @param businessKey 业务键
     * @param processInstanceId 本地流程实例 ID
     */
    public record AssignmentIntent(Long tenantId, Long userId, String taskId,
                                   String businessType, String businessKey,
                                   String processInstanceId) {
    }
}
