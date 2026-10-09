package com.omni.asset.workflow;

import com.omni.asset.client.WorkflowInternalClient;
import com.omni.asset.dto.WorkflowContracts;
import com.omni.common.service.identity.ServiceIdentityContext;
import com.omni.asset.service.AssetOperationWorkflowStateService;
import com.omni.common.core.result.BusinessException;
import com.omni.common.core.result.R;
import feign.FeignException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * 在本地事务提交后协调 Workflow 幂等启动。
 *
 * @author Omni-Stack Team
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AssetWorkflowCoordinator {

    /** 调拨业务类型。 */
    public static final String TRANSFER_BUSINESS_TYPE = "ASSET_TRANSFER";
    /** 处置业务类型。 */
    public static final String DISPOSAL_BUSINESS_TYPE = "ASSET_DISPOSAL";

    private final WorkflowInternalClient workflowInternalClient;
    private final AssetOperationWorkflowStateService workflowStateService;

    /**
     * 使用持久化快照启动 Workflow。
     *
     * @param command 启动快照
     */
    public void start(AssetWorkflowCommand command) {
        if (!command.tenantId().equals(ServiceIdentityContext.requireTenantId())) {
            throw new BusinessException(403, "Workflow 启动快照与当前租户不一致");
        }
        WorkflowContracts.StartRequest request = requestOf(command);
        R<WorkflowContracts.StartResponse> response;
        try {
            response = workflowInternalClient.start(command.tenantId(), request);
        } catch (FeignException exception) {
            log.warn("Asset Workflow 启动结果未知，保留 PENDING 等待同键重试: tenantId={}, operationType={}, operationId={}",
                    command.tenantId(), command.operationType(), command.operationId(), exception);
            throw new BusinessException(503, "Workflow 启动结果待确认，请使用原申请重试");
        } catch (RuntimeException exception) {
            log.error("调用 Asset Workflow 时发生本地异常，保留 PENDING: tenantId={}, operationType={}, operationId={}",
                    command.tenantId(), command.operationType(), command.operationId(), exception);
            throw new BusinessException(503, "Workflow 启动结果待确认，请使用原申请重试");
        }
        if (response != null && response.getCode() == 404) {
            markExplicitFailure(command);
            throw new BusinessException(503, "Workflow 模型版本已不可启动，申请可重试或取消");
        }
        if (response != null && response.getCode() != 200) {
            log.warn("Asset Workflow 返回非成功状态但远端结果可能仍在处理中，保留 PENDING: "
                            + "tenantId={}, operationType={}, operationId={}, responseCode={}",
                    command.tenantId(), command.operationType(), command.operationId(),
                    response.getCode());
            throw new BusinessException(503, "Workflow 启动结果待确认，请使用原申请重试");
        }
        WorkflowContracts.StartResponse data = validateSuccessResponse(command, response);
        try {
            workflowStateService.markStarted(command, data.getProcessInstanceId());
        } catch (RuntimeException exception) {
            log.error("Asset Workflow 已返回成功但本地确认失败，保留 PENDING: tenantId={}, operationType={}, operationId={}",
                    command.tenantId(), command.operationType(), command.operationId(), exception);
            throw new BusinessException(503, "Workflow 已响应但本地状态待确认，请使用原申请重试");
        }
    }

    private void markExplicitFailure(AssetWorkflowCommand command) {
        try {
            workflowStateService.markFailed(command);
        } catch (RuntimeException exception) {
            log.error("Asset Workflow 已明确未启动但本地失败状态写入失败，保留 PENDING: "
                            + "tenantId={}, operationType={}, operationId={}",
                    command.tenantId(), command.operationType(), command.operationId(), exception);
            throw new BusinessException(503, "Workflow 未启动但本地状态待确认，请使用原申请重试");
        }
    }

    private WorkflowContracts.StartRequest requestOf(AssetWorkflowCommand command) {
        WorkflowContracts.StartRequest request = new WorkflowContracts.StartRequest();
        request.setRequestId(command.requestId());
        request.setTenantId(command.tenantId());
        request.setModelVersionId(command.modelVersionId());
        request.setBusinessType(command.businessType());
        request.setBusinessKey(command.businessKey());
        request.setStartUserId(command.startUserId());
        request.setStartUserName(command.startUserName());
        request.setTitle(command.title());
        request.setVariables(command.variables());
        return request;
    }

    private WorkflowContracts.StartResponse validateSuccessResponse(
            AssetWorkflowCommand command, R<WorkflowContracts.StartResponse> response) {
        if (response == null || response.getData() == null) {
            throw new BusinessException(503, "Workflow 启动结果待确认，请使用原申请重试");
        }
        WorkflowContracts.StartResponse data = response.getData();
        if (!command.requestId().equals(data.getRequestId())
                || !command.businessType().equals(data.getBusinessType())
                || !command.businessKey().equals(data.getBusinessKey())
                || data.getProcessInstanceId() == null
                || data.getProcessInstanceId().isBlank()) {
            throw new BusinessException(503, "Workflow 启动响应与本地幂等快照不一致");
        }
        return data;
    }

}
