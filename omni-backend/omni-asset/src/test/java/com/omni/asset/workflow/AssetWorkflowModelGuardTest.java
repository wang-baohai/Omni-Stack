package com.omni.asset.workflow;

import com.omni.asset.client.WorkflowInternalClient;
import com.omni.asset.dto.WorkflowContracts;
import com.omni.common.service.identity.ServiceIdentityContext;
import com.omni.common.service.identity.ServiceRequestIdentity;
import com.omni.common.core.result.BusinessException;
import com.omni.common.core.result.R;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/** Workflow 模型版本失败关闭测试。 */
class AssetWorkflowModelGuardTest {

    /** 清理身份上下文。 */
    @AfterEach
    void clearContext() {
        ServiceIdentityContext.clear();
    }

    /** 只有已发布、有流程定义且分类匹配的模型版本可用于资产审批。 */
    @Test
    void shouldAcceptPublishedModelBoundToExpectedBusinessType() {
        WorkflowInternalClient client = mock(WorkflowInternalClient.class);
        WorkflowContracts.ModelVersionResponse model = model(
                42L, "PUBLISHED", "process:1:42",
                AssetWorkflowCoordinator.TRANSFER_BUSINESS_TYPE);
        when(client.getModelVersion(1L, 42L)).thenReturn(R.ok(model));
        ServiceIdentityContext.set(new ServiceRequestIdentity(7L, 1L, "admin"));

        new AssetWorkflowModelGuard(client).requireStartable(
                42L, AssetWorkflowCoordinator.TRANSFER_BUSINESS_TYPE);
    }

    /** 非发布、空流程定义和身份不一致响应必须失败关闭。 */
    @Test
    void shouldFailClosedForInvalidOrCrossTenantModelResponse() {
        WorkflowInternalClient client = mock(WorkflowInternalClient.class);
        ServiceIdentityContext.set(new ServiceRequestIdentity(7L, 1L, "admin"));
        AssetWorkflowModelGuard guard = new AssetWorkflowModelGuard(client);
        when(client.getModelVersion(1L, 42L))
                .thenReturn(R.ok(model(42L, "DRAFT", "process:1:42",
                        AssetWorkflowCoordinator.TRANSFER_BUSINESS_TYPE)));
        assertThatThrownBy(() -> guard.requireStartable(
                42L, AssetWorkflowCoordinator.TRANSFER_BUSINESS_TYPE))
                .isInstanceOf(BusinessException.class);

        when(client.getModelVersion(1L, 42L))
                .thenReturn(R.ok(model(42L, "PUBLISHED", " ",
                        AssetWorkflowCoordinator.TRANSFER_BUSINESS_TYPE)));
        assertThatThrownBy(() -> guard.requireStartable(
                42L, AssetWorkflowCoordinator.TRANSFER_BUSINESS_TYPE))
                .isInstanceOf(BusinessException.class);

        when(client.getModelVersion(1L, 42L))
                .thenReturn(R.ok(model(99L, "PUBLISHED", "process:2:99",
                        AssetWorkflowCoordinator.TRANSFER_BUSINESS_TYPE)));
        assertThatThrownBy(() -> guard.requireStartable(
                42L, AssetWorkflowCoordinator.TRANSFER_BUSINESS_TYPE))
                .isInstanceOf(BusinessException.class);
    }

    /** 调拨与处置不得交叉复用对方分类的 Workflow 模型。 */
    @Test
    void shouldRejectModelCategoryBoundToAnotherAssetBusinessType() {
        WorkflowInternalClient client = mock(WorkflowInternalClient.class);
        when(client.getModelVersion(1L, 42L)).thenReturn(R.ok(model(
                42L, "PUBLISHED", "process:1:42",
                AssetWorkflowCoordinator.DISPOSAL_BUSINESS_TYPE)));
        ServiceIdentityContext.set(new ServiceRequestIdentity(7L, 1L, "admin"));

        assertThatThrownBy(() -> new AssetWorkflowModelGuard(client).requireStartable(
                42L, AssetWorkflowCoordinator.TRANSFER_BUSINESS_TYPE))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("业务类型不匹配");
    }

    /** 服务端按业务分类解析当前可启动模型，页面无需传递模型版本 ID。 */
    @Test
    void shouldResolveCurrentPublishedModelByBusinessType() {
        WorkflowInternalClient client = mock(WorkflowInternalClient.class);
        WorkflowContracts.ModelVersionResponse model = model(
                42L, "PUBLISHED", "process:1:42",
                AssetWorkflowCoordinator.TRANSFER_BUSINESS_TYPE);
        when(client.getCurrentPublishedModelVersion(
                1L, AssetWorkflowCoordinator.TRANSFER_BUSINESS_TYPE)).thenReturn(R.ok(model));
        ServiceIdentityContext.set(new ServiceRequestIdentity(7L, 1L, "admin"));

        Long modelVersionId = new AssetWorkflowModelGuard(client)
                .resolveStartable(AssetWorkflowCoordinator.TRANSFER_BUSINESS_TYPE);

        assertThat(modelVersionId).isEqualTo(42L);
    }

    /** 自动解析也必须对分类错配和无效响应失败关闭。 */
    @Test
    void shouldFailClosedWhenResolvedModelIsInvalid() {
        WorkflowInternalClient client = mock(WorkflowInternalClient.class);
        when(client.getCurrentPublishedModelVersion(
                1L, AssetWorkflowCoordinator.TRANSFER_BUSINESS_TYPE)).thenReturn(R.ok(model(
                        42L, "PUBLISHED", "process:1:42",
                        AssetWorkflowCoordinator.DISPOSAL_BUSINESS_TYPE)));
        ServiceIdentityContext.set(new ServiceRequestIdentity(7L, 1L, "admin"));

        assertThatThrownBy(() -> new AssetWorkflowModelGuard(client)
                .resolveStartable(AssetWorkflowCoordinator.TRANSFER_BUSINESS_TYPE))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("业务类型不匹配");
    }

    private WorkflowContracts.ModelVersionResponse model(
            Long id, String status, String processDefinitionId, String category) {
        WorkflowContracts.ModelVersionResponse model = new WorkflowContracts.ModelVersionResponse();
        model.setId(id);
        model.setModelKey("asset-approval");
        model.setCategory(category);
        model.setStatus(status);
        model.setProcessDefinitionId(processDefinitionId);
        return model;
    }
}
