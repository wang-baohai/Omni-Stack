package com.omni.srm.workflow;

import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.omni.common.core.result.BusinessException;
import com.omni.common.core.result.R;
import com.omni.srm.client.WorkflowInternalClient;
import com.omni.srm.domain.SrmStateMachine;
import com.omni.srm.dto.WorkflowContracts;
import com.omni.srm.entity.SrmSupplier;
import com.omni.srm.mapper.SrmSupplierMapper;
import com.omni.common.service.identity.ServiceIdentityContext;
import com.omni.common.service.identity.ServiceRequestIdentity;
import feign.FeignException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/**
 * 在供应商本地事务提交后协调 Workflow 幂等启动。
 *
 * @author Omni-Stack Team
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class SupplierWorkflowCoordinator {

    private final WorkflowInternalClient workflowInternalClient;
    private final SupplierWorkflowStateService workflowStateService;
    private final SrmSupplierMapper supplierMapper;

    /**
     * 使用已持久化快照启动 Workflow；任何不确定失败均保留原幂等键供重试。
     *
     * @param command 已持久化启动快照
     */
    public void start(SupplierWorkflowCommand command) {
        if (!command.tenantId().equals(ServiceIdentityContext.requireTenantId())) {
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
            throw new BusinessException(503, "Workflow 启动结果未确认，请稍后重试");
        }
    }

    /**
     * 准备并启动供应商准入工作流：查询模型版本、持久化快照字段、发起 Feign 调用。
     * 启动失败时供应商保留 PENDING_REVIEW 状态 + FAILED 标记，不抛异常。
     *
     * @param supplier 已持久化的供应商实体（状态须为 PENDING_REVIEW）
     */
    public void prepareAndStart(SrmSupplier supplier) {
        Long tenantId = supplier.getTenantId();
        int nextAttempt = (supplier.getApprovalAttempt() == null ? 0 : supplier.getApprovalAttempt()) + 1;
        String requestId = UUID.randomUUID().toString();
        String businessKey = supplier.getId() + ":" + nextAttempt;
        Long modelVersionId = resolveModelVersionId(tenantId);

        supplierMapper.update(null, new LambdaUpdateWrapper<SrmSupplier>()
                .eq(SrmSupplier::getId, supplier.getId())
                .eq(SrmSupplier::getDeleted, 0)
                .set(SrmSupplier::getWorkflowRequestId, requestId)
                .set(SrmSupplier::getWorkflowBusinessKey, businessKey)
                .set(SrmSupplier::getWorkflowModelVersionId, modelVersionId)
                .set(SrmSupplier::getApprovalAttempt, nextAttempt)
                .set(SrmSupplier::getWorkflowStartStatus, SrmStateMachine.START_PENDING)
                .set(SrmSupplier::getProcessInstanceId, null)
                .set(SrmSupplier::getWorkflowCompletedTime, null));

        supplier.setApprovalAttempt(nextAttempt);
        ServiceRequestIdentity identity = ServiceIdentityContext.require();
        SupplierWorkflowCommand command = new SupplierWorkflowCommand(
                supplier.getId(), tenantId, supplier.getSupplierNo(),
                supplier.getName(), supplier.getCategoryCode(),
                nextAttempt, requestId, businessKey, modelVersionId,
                identity.userId(), identity.username());
        try {
            start(command);
        } catch (RuntimeException startException) {
            log.warn("供应商准入工作流启动失败，供应商保留 PENDING_REVIEW 状态: tenantId={}, supplierId={}",
                    tenantId, supplier.getId(), startException);
        }
    }

    /**
     * 终止供应商准入流程实例。
     *
     * @param tenantId          租户 ID
     * @param processInstanceId 流程实例 ID
     * @param reason            终止原因
     */
    public void terminate(Long tenantId, String processInstanceId, String reason) {
        try {
            workflowInternalClient.terminate(tenantId, processInstanceId, reason);
        } catch (FeignException exception) {
            throw new BusinessException(503, "Workflow 服务暂时不可用，无法终止流程");
        }
    }

    private Long resolveModelVersionId(Long tenantId) {
        try {
            R<WorkflowContracts.ModelVersionResponse> response =
                    workflowInternalClient.getCurrentPublishedModelVersion(
                            tenantId, WorkflowContracts.BUSINESS_TYPE);
            WorkflowContracts.ModelVersionResponse data = response == null ? null : response.getData();
            if (response != null && response.getCode() == 200 && data != null
                    && WorkflowContracts.BUSINESS_TYPE.equals(data.getCategory())
                    && "PUBLISHED".equals(data.getStatus())
                    && data.getProcessDefinitionId() != null
                    && !data.getProcessDefinitionId().isBlank()) {
                return data.getId();
            }
        } catch (RuntimeException feignException) {
            log.warn("查询供应商准入工作流模型版本失败: tenantId={}", tenantId, feignException);
        }
        throw new BusinessException(503, "未找到已发布的供应商准入审批流程模型，请联系管理员配置");
    }

    private WorkflowContracts.StartRequest requestOf(SupplierWorkflowCommand command) {
        Map<String, Object> variables = new LinkedHashMap<>();
        variables.put("supplierId", command.supplierId());
        variables.put("approvalAttempt", command.approvalAttempt());
        variables.put("categoryCode", command.categoryCode());
        variables.put("supplierName", command.supplierName());

        WorkflowContracts.StartRequest request = new WorkflowContracts.StartRequest();
        request.setRequestId(command.requestId());
        request.setTenantId(command.tenantId());
        request.setModelVersionId(command.modelVersionId());
        request.setBusinessType(WorkflowContracts.BUSINESS_TYPE);
        request.setBusinessKey(command.businessKey());
        request.setStartUserId(command.startUserId());
        request.setStartUserName(command.startUserName());
        request.setTitle("供应商准入审批 " + command.supplierNo() + " - " + command.supplierName());
        request.setVariables(variables);
        return request;
    }

    private WorkflowContracts.StartResponse validateResponse(
            SupplierWorkflowCommand command, R<WorkflowContracts.StartResponse> response) {
        if (response == null || response.getCode() != 200 || response.getData() == null) {
            throw new BusinessException(503, "Workflow 返回了无效的启动响应");
        }
        WorkflowContracts.StartResponse data = response.getData();
        if (!command.requestId().equals(data.getRequestId())
                || !WorkflowContracts.BUSINESS_TYPE.equals(data.getBusinessType())
                || !command.businessKey().equals(data.getBusinessKey())
                || data.getProcessInstanceId() == null
                || data.getProcessInstanceId().isBlank()) {
            throw new BusinessException(503, "Workflow 启动响应与本地幂等快照不一致");
        }
        return data;
    }

    private void markFailedSafely(SupplierWorkflowCommand command, RuntimeException cause) {
        try {
            workflowStateService.markFailed(command);
        } catch (RuntimeException markException) {
            cause.addSuppressed(markException);
            log.error("标记供应商 Workflow 启动失败状态时发生异常: tenantId={}, supplierId={}, businessKey={}",
                    command.tenantId(), command.supplierId(), command.businessKey(), markException);
        }
    }
}
