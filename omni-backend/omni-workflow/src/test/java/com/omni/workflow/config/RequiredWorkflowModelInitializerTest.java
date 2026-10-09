package com.omni.workflow.config;

import com.omni.workflow.entity.WfProcessModel;
import com.omni.workflow.mapper.WfProcessModelMapper;
import com.omni.workflow.mapper.WfProcessModelVersionMapper;
import com.omni.workflow.service.WorkflowModelService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentMatchers;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.boot.ApplicationArguments;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 必需流程模型自动发布测试。
 */
@ExtendWith(MockitoExtension.class)
class RequiredWorkflowModelInitializerTest {

    @Mock private WfProcessModelMapper modelMapper;
    @Mock private WfProcessModelVersionMapper versionMapper;
    @Mock private WorkflowModelService workflowModelService;
    @Mock private ApplicationArguments applicationArguments;

    /** 小写或旧分类必须在启动时校正并自动发布。 */
    @Test
    void shouldNormalizeAndPublishRequiredDefaultModels() {
        List<WfProcessModel> models = List.of(
                model(2L, "procurement-approval", "PROCUREMENT_REQUISITION"),
                model(3L, "asset-transfer", "asset_transfer"),
                model(4L, "asset-disposal", "asset_disposal"),
                model(5L, "supplier-onboarding", "supplier"));
        when(modelMapper.selectList(ArgumentMatchers.any())).thenReturn(models);
        RequiredWorkflowModelInitializer initializer = new RequiredWorkflowModelInitializer(
                modelMapper, versionMapper, workflowModelService);

        initializer.run(applicationArguments);

        assertThat(models).extracting(WfProcessModel::getCategory)
                .containsExactly("purchase", "ASSET_TRANSFER", "ASSET_DISPOSAL", "SRM_SUPPLIER_ONBOARDING");
        verify(workflowModelService).publishModel(2L, "system");
        verify(workflowModelService).publishModel(3L, "system");
        verify(workflowModelService).publishModel(4L, "system");
        verify(workflowModelService).publishModel(5L, "system");
    }

    private WfProcessModel model(Long id, String key, String category) {
        WfProcessModel model = new WfProcessModel();
        model.setId(id);
        model.setTenantId(1L);
        model.setModelKey(key);
        model.setCategory(category);
        model.setStatus(1);
        model.setCurrentDraftVersionId(id);
        return model;
    }
}
