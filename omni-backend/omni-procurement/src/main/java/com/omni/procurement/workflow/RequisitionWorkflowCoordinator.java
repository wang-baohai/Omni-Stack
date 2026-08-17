package com.omni.procurement.workflow;

import com.omni.common.core.result.BusinessException;
import com.omni.common.core.result.R;
import com.omni.procurement.client.WorkflowInternalClient;
import com.omni.procurement.dto.WorkflowContracts;
import com.omni.procurement.security.ProcTenantContext;
import com.omni.procurement.service.RequisitionWorkflowStateService;
import feign.FeignException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 在请购本地事务提交后协调 Workflow 幂等启动。
 *
 * @author Omni-Stack Team
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class RequisitionWorkflowCoordinator {

    /** 请购跨服务业务类型。 */
    public static final String BUSINESS_TYPE = "PROCUREMENT_REQUISITION";

    private final WorkflowInternalClient workflowInternalClient;
    private final RequisitionWorkflowStateService workflowStateService;

    /**
     * 使用已持久化快照启动 Workflow；任何不确定失败均保留原幂等键供重试。
     *
     * @param command 已持久化启动快照
     */
    public void start(RequisitionWorkflowCommand command) {
        if (!command.tenantId().equals(ProcTenantContext.requireTenantId())) {
            throw new BusinessException(403, "Workflow 启动快照与当前租户不一致");
        }
        try {
            WorkflowContracts.StartRequest request = requestOf(command);
            R<WorkflowContracts.StartResponse> response =
                    workflowInternalClient.start(command.tenantId(), request);
            WorkflowContracts.StartResponse data = validateResponse(command, response);
            workflowStateService.markStarted(command, data.getProcessInstanceId());
        } catch (FeignException exception) {
            markFailedSafely(command, exception);
            throw new BusinessException(503, "Workflow 服务暂时不可用，请稍后重试启动");
        } catch (RuntimeException exception) {
            markFailedSafely(command, exception);
            throw new BusinessException(503, "Workflow 启动结果未确认，请稍后使用原审批轮次重试");
        }
    }

    private WorkflowContracts.StartRequest requestOf(RequisitionWorkflowCommand command) {
        Map<String, Object> variables = new LinkedHashMap<>();
        variables.put("requisitionId", command.requisitionId());
        variables.put("approvalAttempt", command.approvalAttempt());
        variables.put("materialCategory", command.categoryCode());
        variables.put("totalAmount", command.totalAmount().toPlainString());
        variables.put("requesterUnitId", command.requesterUnitId());

        WorkflowContracts.StartRequest request = new WorkflowContracts.StartRequest();
        request.setRequestId(command.requestId());
        request.setTenantId(command.tenantId());
        request.setModelVersionId(command.modelVersionId());
        request.setBusinessType(BUSINESS_TYPE);
        request.setBusinessKey(command.businessKey());
        request.setStartUserId(command.requesterUserId());
        request.setStartUserName(null);
        request.setTitle("采购申请 " + command.requisitionNo() + " - " + command.title());
        request.setVariables(variables);
        return request;
    }

    private WorkflowContracts.StartResponse validateResponse(
            RequisitionWorkflowCommand command, R<WorkflowContracts.StartResponse> response) {
        if (response == null || response.getCode() != 200 || response.getData() == null) {
            throw new BusinessException(503, "Workflow 返回了无效的启动响应");
        }
        WorkflowContracts.StartResponse data = response.getData();
        if (!command.requestId().equals(data.getRequestId())
                || !BUSINESS_TYPE.equals(data.getBusinessType())
                || !command.businessKey().equals(data.getBusinessKey())
                || data.getProcessInstanceId() == null
                || data.getProcessInstanceId().isBlank()) {
            throw new BusinessException(503, "Workflow 启动响应与本地幂等快照不一致");
        }
        return data;
    }

    private void markFailedSafely(RequisitionWorkflowCommand command, RuntimeException cause) {
        try {
            workflowStateService.markFailed(command);
        } catch (RuntimeException markException) {
            cause.addSuppressed(markException);
            log.error("标记请购 Workflow 启动失败状态时发生异常: tenantId={}, requisitionId={}, businessKey={}",
                    command.tenantId(), command.requisitionId(), command.businessKey(), markException);
        }
    }
}
