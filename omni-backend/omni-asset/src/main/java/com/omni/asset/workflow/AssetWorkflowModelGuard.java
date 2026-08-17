package com.omni.asset.workflow;

import com.omni.asset.client.WorkflowInternalClient;
import com.omni.asset.dto.WorkflowContracts;
import com.omni.asset.security.AssetTenantContext;
import com.omni.common.core.result.BusinessException;
import com.omni.common.core.result.R;
import feign.FeignException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * Workflow 模型版本失败关闭校验器。
 *
 * @author Omni-Stack Team
 */
@Component
@RequiredArgsConstructor
public class AssetWorkflowModelGuard {

    private final WorkflowInternalClient workflowInternalClient;

    /**
     * 按业务分类解析当前租户可启动的模型版本。
     *
     * @param businessType 资产审批业务类型
     * @return 模型版本 ID
     */
    public Long resolveStartable(String businessType) {
        if (businessType == null || businessType.isBlank()) {
            throw new BusinessException(400, "资产审批业务类型不能为空");
        }
        Long tenantId = AssetTenantContext.requireTenantId();
        try {
            R<WorkflowContracts.ModelVersionResponse> response =
                    workflowInternalClient.getCurrentPublishedModelVersion(tenantId, businessType);
            WorkflowContracts.ModelVersionResponse data = response == null ? null : response.getData();
            requireValidResponse(response, data, null, businessType);
            return data.getId();
        } catch (FeignException.Forbidden exception) {
            throw new BusinessException(403, "Workflow 拒绝模型版本解析");
        } catch (FeignException exception) {
            throw new BusinessException(503, "Workflow 模型解析服务暂时不可用");
        }
    }

    /**
     * 校验模型版本属于当前租户、已发布、存在流程定义且绑定指定业务类型。
     *
     * @param modelVersionId 模型版本 ID
     * @param businessType 资产审批业务类型
     */
    public void requireStartable(Long modelVersionId, String businessType) {
        if (businessType == null || businessType.isBlank()) {
            throw new BusinessException(400, "资产审批业务类型不能为空");
        }
        Long tenantId = AssetTenantContext.requireTenantId();
        try {
            R<WorkflowContracts.ModelVersionResponse> response =
                    workflowInternalClient.getModelVersion(tenantId, modelVersionId);
            WorkflowContracts.ModelVersionResponse data =
                    response == null ? null : response.getData();
            requireValidResponse(response, data, modelVersionId, businessType);
        } catch (FeignException.Forbidden exception) {
            throw new BusinessException(403, "Workflow 拒绝模型版本校验");
        } catch (FeignException exception) {
            throw new BusinessException(503, "Workflow 模型校验服务暂时不可用");
        }
    }

    private void requireValidResponse(R<WorkflowContracts.ModelVersionResponse> response,
                                      WorkflowContracts.ModelVersionResponse data,
                                      Long expectedId, String businessType) {
        if (response == null || response.getCode() != 200 || data == null) {
            throw new BusinessException(503, "Workflow 模型校验服务暂时不可用");
        }
        if (data.getId() == null
                || expectedId != null && !expectedId.equals(data.getId())
                || !"PUBLISHED".equals(data.getStatus())
                || data.getProcessDefinitionId() == null
                || data.getProcessDefinitionId().isBlank()
                || data.getModelKey() == null
                || data.getModelKey().isBlank()) {
            throw new BusinessException(409, "Workflow 模型版本不可用于启动审批");
        }
        if (!businessType.equals(data.getCategory())) {
            throw new BusinessException(409, "Workflow 模型分类与资产审批业务类型不匹配");
        }
    }
}
